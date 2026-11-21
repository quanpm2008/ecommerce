package com.example.identity_service.seller;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Shop names are unique among sellers whose application is PENDING or APPROVED (G-01, REQ-IDN-002); a REJECTED
 * seller frees its name, and customers may share names freely. The name lives in {@code users.full_name}, so no single
 * index can enforce this: the name is locked for the rest of the transaction, then checked.
 */
@Component
@RequiredArgsConstructor
public class ShopNames {

	private final SellerRepository sellers;

	/**
	 * In the caller's transaction: locks {@code name} and refuses it (409 {@code SELLER_NAME_ALREADY_USED} on
	 * {@code fullName}) when another active seller holds it. {@code sellerId} is the account applying.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void reserve(String name, UUID sellerId) {
		sellers.lockShopName(name);
		if (sellers.existsActiveShopName(name, sellerId)) {
			throw ApiException.forField(ErrorCode.SELLER_NAME_ALREADY_USED, "fullName", "DUPLICATE");
		}
	}

}
