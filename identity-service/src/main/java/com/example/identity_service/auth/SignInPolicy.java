package com.example.identity_service.auth;

import com.example.identity_service.admin.ModerationService;
import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.seller.SellerApprovalStatus;
import com.example.identity_service.seller.SellerRepository;
import com.example.identity_service.user.AccountType;
import com.example.identity_service.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Who may get a session, checked the same way on login and on refresh (API §3.2, §6.1), after the credentials so the
 * answer never tells a stranger whether an account exists:
 * <ul>
 * <li>a seller account only once its application is APPROVED (403 {@code SELLER_PENDING_APPROVAL} /
 * {@code SELLER_REJECTED});</li>
 * <li>a banned account is first reconciled with its current obligations ({@link #reconcile}, G-14): a late obligation
 * reopens a limited session, none left makes it DISABLED; a source that cannot answer is 503.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class SignInPolicy {

	private final SellerRepository sellers;

	private final ModerationService moderation;

	/** G-14 for login and refresh; runs outside the session's transaction. 503 if the obligations can't be read. */
	void reconcile(UUID userId) {
		moderation.reconcile(userId);
	}

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
