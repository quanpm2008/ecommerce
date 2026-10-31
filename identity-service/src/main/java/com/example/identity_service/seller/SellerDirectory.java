package com.example.identity_service.seller;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.common.persistence.LikePattern;
import com.example.identity_service.media.MediaStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Public seller lookup (UC-B02): only sellers visible under G-06, as {@link SellerPublic}. A seller that exists but
 * is not visible is indistinguishable from one that does not exist (404).
 */
@Service
@RequiredArgsConstructor
public class SellerDirectory {

	/** The API defines no {@code sort} here; name order (then id, for stable pages) is our choice. */
	private static final Sort ORDER = Sort.by(Sort.Order.asc("sellerName").ignoreCase(), Sort.Order.asc("sellerId"));

	private final SellerRepository sellers;

	private final MediaStorage media;

	@Transactional(readOnly = true)
	public Page<SellerPublic> search(String q, Pageable pageable) {
		Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ORDER);
		return sellers.findPublic(LikePattern.contains(q), page).map(this::view);
	}

	@Transactional(readOnly = true)
	public SellerPublic get(UUID sellerId) {
		return sellers.findPublicById(sellerId).map(this::view)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Seller not found"));
	}

	private SellerPublic view(Seller seller) {
		String key = seller.getLogoObjectKey();
		return SellerPublic.of(seller, key == null ? null : media.readUrl(key));
	}

}
