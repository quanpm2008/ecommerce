package com.example.identity_service.admin;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.common.persistence.LikePattern;
import com.example.identity_service.customer.CustomerRepository;
import com.example.identity_service.customer.CustomerStatus;
import com.example.identity_service.seller.SellerApprovalStatus;
import com.example.identity_service.seller.SellerRepository;
import com.example.identity_service.seller.SellerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Admin read side (UC-E01, E06, `operations:read:any`): customer and seller lists with search, filters and the sort
 * keys the API names, plus details. Unknown sort keys are a 400 rather than silently ignored.
 */
@Service
@RequiredArgsConstructor
public class AdminDirectory {

	/** API §6.1: {@code sort: createdAt, fullName}; newest first by default. */
	private static final Map<String, String> CUSTOMER_SORTS = Map.of("createdAt", "createdAt", "fullName",
			"user.fullName");

	private static final Sort CUSTOMER_DEFAULT = Sort.by(Sort.Direction.DESC, "createdAt");

	/** The pending queue uses {@code registeredAt,asc}; newest first by default. */
	private static final Map<String, String> SELLER_SORTS = Map.of("registeredAt", "registeredAt", "sellerName",
			"sellerName");

	private static final Sort SELLER_DEFAULT = Sort.by(Sort.Direction.DESC, "registeredAt");

	private final CustomerRepository customers;

	private final SellerRepository sellers;

	private final AdminViews views;

	@Transactional(readOnly = true)
	public Page<AdminViews.CustomerAdminView> customers(String q, CustomerStatus status, Pageable pageable) {
		Pageable page = page(pageable, CUSTOMER_SORTS, CUSTOMER_DEFAULT, "customerId");
		return customers.findForAdmin(status, LikePattern.contains(q), page).map(views::customer);
	}

	@Transactional(readOnly = true)
	public AdminViews.CustomerAdminView customer(UUID customerId) {
		return customers.findById(customerId).map(views::customer)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Customer not found"));
	}

	@Transactional(readOnly = true)
	public Page<AdminViews.SellerAdminView> sellers(String q, SellerApprovalStatus approvalStatus,
			SellerStatus status, Pageable pageable) {
		Pageable page = page(pageable, SELLER_SORTS, SELLER_DEFAULT, "sellerId");
		return sellers.findForAdmin(approvalStatus, status, LikePattern.contains(q), page).map(views::seller);
	}

	@Transactional(readOnly = true)
	public AdminViews.SellerAdminView seller(UUID sellerId) {
		return sellers.findById(sellerId).map(views::seller)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Seller not found"));
	}

	/** Maps API sort keys to entity paths and adds the id as a tie-breaker so pages are stable. */
	private static Pageable page(Pageable requested, Map<String, String> allowed, Sort fallback, String idPath) {
		Sort sort = Sort.unsorted();
		for (Sort.Order order : requested.getSort()) {
			String path = allowed.get(order.getProperty());
			if (path == null) {
				throw ApiException.forField(ErrorCode.VALIDATION_ERROR, "sort", "UNSUPPORTED_SORT");
			}
			sort = sort.and(Sort.by(order.getDirection(), path));
		}
		Sort base = sort.isSorted() ? sort : fallback;
		return PageRequest.of(requested.getPageNumber(), requested.getPageSize(), base.and(Sort.by(idPath)));
	}

}
