package com.example.identity_service.admin;

import com.example.identity_service.account.CustomerProfile;
import com.example.identity_service.account.SelfProfileAssembler;
import com.example.identity_service.account.SellerSelfProfile;
import com.example.identity_service.customer.Customer;
import com.example.identity_service.seller.Seller;
import com.example.identity_service.seller.SellerApplication;
import com.example.identity_service.seller.SellerApprovalStatus;
import com.example.identity_service.user.AccountType;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin DTOs (API §5, `operations:read:any` only). They are built from explicit fields, so password hash, Google
 * subject and tokens can never appear.
 */
@Component
@RequiredArgsConstructor
public class AdminViews {

	private final SelfProfileAssembler profiles;

	public CustomerAdminView customer(Customer customer) {
		return new CustomerAdminView(UserSummary.of(customer.getUser()), profiles.customer(customer),
				customer.getHasPaidOrderBeforeBan(), customer.getBanReason());
	}

	public SellerAdminView seller(Seller seller) {
		return new SellerAdminView(UserSummary.of(seller.getUser()), profiles.seller(seller),
				seller.getReviewedByUserId());
	}

	public record UserSummary(UUID userId, String email, String fullName, String phoneNumber, AccountType accountType,
			UserStatus userStatus, long authzVersion) {

		static UserSummary of(User user) {
			return new UserSummary(user.getUserId(), user.getEmail(), user.getFullName(), user.getPhoneNumber(),
					user.getAccountType(), user.getStatus(), user.getAuthzVersion());
		}

	}

	/** One submission of a seller application, as it was sent and decided. */
	public record SellerApplicationView(UUID applicationId, int submissionNo, SellerApprovalStatus status,
			String fullName, String phoneNumber, String description, Instant submittedAt, Instant reviewedAt,
			UUID reviewedByUserId, String rejectionReason) {

		static SellerApplicationView of(SellerApplication application) {
			return new SellerApplicationView(application.getApplicationId(), application.getSubmissionNo(),
					application.getStatus(), application.getSnapshotFullName(), application.getSnapshotPhone(),
					application.getDescription(), application.getSubmittedAt(), application.getReviewedAt(),
					application.getReviewedByUserId(), application.getRejectionReason());
		}

	}

	/**
	 * {@code hasPaidOrder} is Order's answer stored with the current ban (G-14), null when not banned: Identity
	 * cannot see orders, and the FE gets order counts from Order.
	 */
	public record CustomerAdminView(UserSummary user, CustomerProfile customerProfile, Boolean hasPaidOrder,
			String banReason) {
	}

	public record SellerAdminView(UserSummary user, SellerSelfProfile sellerProfile, UUID reviewedByUserId) {
	}

}
