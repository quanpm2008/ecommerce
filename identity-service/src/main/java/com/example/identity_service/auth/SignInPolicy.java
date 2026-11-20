package com.example.identity_service.auth;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.seller.SellerApprovalStatus;
import com.example.identity_service.seller.SellerRepository;
import com.example.identity_service.user.AccountType;
import com.example.identity_service.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Who may get a session, checked the same way on login and on refresh (API §3.2, §6.1): a seller account only once its
 * application is APPROVED (403 {@code SELLER_PENDING_APPROVAL} / {@code SELLER_REJECTED}). Checked after the
 * credentials, so the answer never tells a stranger whether an account exists.
 */
@Component
@RequiredArgsConstructor
class SignInPolicy {

	private final SellerRepository sellers;

	void check(User user) {
		if (user.getAccountType() != AccountType.SELLER) {
			return;
		}
		SellerApprovalStatus approval = sellers.findById(user.getUserId())
				.map(seller -> seller.getApprovalStatus())
				.orElse(SellerApprovalStatus.PENDING);
		switch (approval) {
			case PENDING -> throw new ApiException(ErrorCode.SELLER_PENDING_APPROVAL);
			case REJECTED -> throw new ApiException(ErrorCode.SELLER_REJECTED);
			case APPROVED -> {
			}
		}
	}

}
