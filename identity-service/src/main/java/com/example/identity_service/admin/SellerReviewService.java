package com.example.identity_service.admin;

import com.example.identity_service.audit.AuditAction;
import com.example.identity_service.audit.AuditState;
import com.example.identity_service.audit.AuditTargetType;
import com.example.identity_service.audit.AuditTrail;
import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.customer.CustomerRepository;
import com.example.identity_service.outbox.EventActor;
import com.example.identity_service.outbox.OutboxWriter;
import com.example.identity_service.rbac.RoleAssignments;
import com.example.identity_service.rbac.RoleCode;
import com.example.identity_service.seller.Seller;
import com.example.identity_service.seller.SellerApplication;
import com.example.identity_service.seller.SellerApplicationRepository;
import com.example.identity_service.seller.SellerApprovalStatus;
import com.example.identity_service.seller.SellerRepository;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Seller application review (UC-E06, `seller:approve`). Each decision is one Identity transaction: profile, role,
 * {@code authzVersion}, audit row and outbox event together. The user row is locked first, so two admins deciding at
 * once cannot both succeed: the second gets {@code ALREADY_PROCESSED}.
 */
@Service
@RequiredArgsConstructor
public class SellerReviewService {

	private final UserRepository users;

	private final SellerRepository sellers;

	private final SellerApplicationRepository applications;

	private final CustomerRepository customers;

	private final RoleAssignments roleAssignments;

	private final AuditTrail audit;

	private final OutboxWriter outbox;

	private final AdminViews views;

	private final Clock clock;

	/**
	 * The latest application, PENDING → APPROVED, and the SELLER role. Finance creates the wallet from
	 * {@code seller.approved}.
	 */
	@Transactional
	public AdminViews.SellerAdminView approve(UUID adminId, UUID sellerId) {
		Review review = start(adminId, sellerId);
		Instant now = clock.instant();
		review.application().approve(adminId, now);
		review.seller().approve(adminId, now);
		if (!roleAssignments.grant(review.user(), RoleCode.SELLER, adminId)) {
			review.user().bumpAuthzVersion(); // the role was already active; the approval still changes access
		}
		return finish(adminId, review, AuditAction.APPROVE, "seller.approved", null);
	}

	/**
	 * The latest application, PENDING → REJECTED, with a P-21d reason. No role, no sign-in; the account and the
	 * application's row stay, and the shop name becomes free again.
	 */
	@Transactional
	public AdminViews.SellerAdminView reject(UUID adminId, UUID sellerId, String reason) {
		String checked = AdminReasons.check(reason, AdminReasons.SELLER_REJECTION);
		Review review = start(adminId, sellerId);
		Instant now = clock.instant();
		review.application().reject(adminId, checked, now);
		review.seller().reject(adminId, checked, now);
		review.user().bumpAuthzVersion();
		return finish(adminId, review, AuditAction.REJECT, "seller.rejected", checked);
	}

	private Review start(UUID adminId, UUID sellerId) {
		User user = users.findLockedById(sellerId).orElseThrow(SellerReviewService::notFound);
		Seller seller = sellers.findById(sellerId).orElseThrow(SellerReviewService::notFound);
		if (adminId.equals(sellerId)) {
			throw new ApiException(ErrorCode.SELF_APPROVAL_FORBIDDEN);
		}
		SellerApplication latest = applications.findFirstBySellerIdOrderBySubmissionNoDesc(sellerId)
				.orElseThrow(SellerReviewService::notFound);
		if (!seller.isPending() || latest.getStatus() != SellerApprovalStatus.PENDING) {
			throw ApiException.currentStatus(ErrorCode.ALREADY_PROCESSED, seller.getApprovalStatus());
		}
		return new Review(user, seller, latest, capture(user, seller));
	}

	private AdminViews.SellerAdminView finish(UUID adminId, Review review, AuditAction action, String eventType,
			String reason) {
		User user = review.user();
		Seller seller = review.seller();
		audit.record(adminId, action, AuditTargetType.SELLER, seller.getSellerId(), reason, review.before(),
				capture(user, seller));
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("userId", user.getUserId());
		payload.put("sellerId", seller.getSellerId());
		payload.put("approvalStatus", seller.getApprovalStatus());
		payload.put("authzVersion", user.getAuthzVersion());
		if (reason != null) {
			payload.put("reason", reason);
		}
		outbox.write(eventType, seller.getSellerId(), EventActor.user(adminId, EventActor.Context.ADMIN), payload);
		return views.seller(seller);
	}

	private AuditState capture(User user, Seller seller) {
		return AuditState.capture(user, customers.findById(user.getUserId()).orElse(null), seller);
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.NOT_FOUND, "Seller not found");
	}

	private record Review(User user, Seller seller, SellerApplication application, AuditState before) {
	}

}
