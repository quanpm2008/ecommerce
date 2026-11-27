package com.example.identity_service.service;

import com.example.identity_service.dto.CustomerProfile;
import com.example.identity_service.dto.SellerSelfProfile;
import com.example.identity_service.entity.Customer;
import com.example.identity_service.entity.Seller;
import com.example.identity_service.entity.SellerApplication;
import com.example.identity_service.entity.SellerApprovalStatus;
import com.example.identity_service.entity.CustomerStatus;
import com.example.identity_service.entity.SellerStatus;
import com.example.identity_service.entity.AccessMode;
import com.example.identity_service.entity.AccountStatus;
import com.example.identity_service.entity.AccountType;
import com.example.identity_service.entity.User;
import com.example.identity_service.entity.UserStatus;
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

	/** {@code banImpact} only on the detail views (null in lists, and when a source cannot answer). */
	public CustomerAdminView customer(Customer customer, ModerationService.BanImpact banImpact) {
		return new CustomerAdminView(UserSummary.of(customer.getUser(), customer.getStatus() == CustomerStatus.BANNED),
				profiles.customer(customer), customer.getBanReason(), banImpact);
	}

	public SellerAdminView seller(Seller seller, ModerationService.BanImpact banImpact) {
		return new SellerAdminView(UserSummary.of(seller.getUser(), seller.getStatus() == SellerStatus.BANNED),
				profiles.seller(seller), seller.getReviewedByUserId(), banImpact);
	}

	/** API §6.1 {@code UserSummary}. */
	public record UserSummary(UUID userId, String email, String fullName, String phoneNumber, AccountType accountType,
			AccountStatus accountStatus, UserStatus userStatus, AccessMode accessMode, Instant bannedAt,
			Instant disabledAt, Instant restoreUntil, long authzVersion) {

		static UserSummary of(User user, boolean profileBanned) {
			return new UserSummary(user.getUserId(), user.getEmail(), user.getFullName(), user.getPhoneNumber(),
					user.getAccountType(), AccountStatus.of(user, profileBanned), user.getStatus(), user.getAccessMode(),
					user.getBannedAt(), user.getDisabledAt(), user.getRestoreUntil(), user.getAuthzVersion());
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

	/** API §6.1 {@code CustomerAdminView}: no ban history or credentials. */
	public record CustomerAdminView(UserSummary user, CustomerProfile customerProfile, String banReason,
			ModerationService.BanImpact banImpact) {
	}

	/** API §6.1 {@code SellerAdminView}. */
	public record SellerAdminView(UserSummary user, SellerSelfProfile sellerProfile, UUID reviewedByUserId,
			ModerationService.BanImpact banImpact) {
	}

}
