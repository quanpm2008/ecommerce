package com.example.identity_service.admin;

import com.example.identity_service.audit.AuditAction;
import com.example.identity_service.audit.AuditState;
import com.example.identity_service.audit.AuditTargetType;
import com.example.identity_service.audit.AuditTrail;
import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.customer.Customer;
import com.example.identity_service.customer.CustomerBanMode;
import com.example.identity_service.customer.CustomerRepository;
import com.example.identity_service.customer.CustomerStatus;
import com.example.identity_service.order.OrderClient;
import com.example.identity_service.outbox.EventActor;
import com.example.identity_service.outbox.OutboxWriter;
import com.example.identity_service.rbac.PermissionResolver;
import com.example.identity_service.rbac.RoleCode;
import com.example.identity_service.seller.Seller;
import com.example.identity_service.seller.SellerRepository;
import com.example.identity_service.seller.SellerStatus;
import com.example.identity_service.session.SessionService;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import com.example.identity_service.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Scoped ban and unban (API §6.1, G-14, `account:moderate`). Each change locks the user row and commits the new
 * status, the {@code authzVersion} bump, the audit row and the outbox event together. Repeating the current state is
 * a 200 with nothing written; a different ban reason while banned is a 409, so history is never overwritten.
 *
 * <p>A CUSTOMER ban needs Order's {@code hasPaidOrder}. Order is called outside any transaction: the first pass
 * checks everything and stops before changing anything if it needs the answer; the second applies the ban with the
 * answer, re-checking under the lock. Order unavailable → 503 and nothing changed.
 */
@Service
@RequiredArgsConstructor
public class ModerationService {

	private final UserRepository users;

	private final CustomerRepository customers;

	private final SellerRepository sellers;

	private final PermissionResolver permissions;

	private final SessionService sessions;

	private final OrderClient orders;

	private final AuditTrail audit;

	private final OutboxWriter outbox;

	private final TransactionTemplate tx;

	public BanResult ban(UUID adminId, UUID userId, ModerationScope scope, String reason) {
		String banReason = reason.strip();
		BanResult result = tx.execute(status -> applyBan(adminId, userId, scope, banReason, null));
		if (result == null) {
			boolean hasPaidOrder = orders.hasPaidOrder(userId);
			result = tx.execute(status -> applyBan(adminId, userId, scope, banReason, hasPaidOrder));
		}
		return result;
	}

	public UnbanResult unban(UUID adminId, UUID userId, ModerationScope scope, String reason) {
		String unbanReason = reason == null || reason.isBlank() ? null : reason.strip();
		return tx.execute(status -> applyUnban(adminId, userId, scope, unbanReason));
	}

	/** Null means "needs Order's answer"; nothing has been changed then. */
	private BanResult applyBan(UUID adminId, UUID userId, ModerationScope scope, String reason,
			Boolean hasPaidOrder) {
		Target target = lock(userId);
		if (permissions.activeRolesOf(target.user()).contains(RoleCode.ADMIN)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "Users with the ADMIN role cannot be banned");
		}
		User user = target.user();
		AuditState before = target.state();
		switch (scope) {
			case USER -> {
				if (!user.isActive()) {
					return unchangedBan(target, scope, user.getDisabledReason(), reason, UserStatus.DISABLED);
				}
				user.disable(reason);
				sessions.revokeAllOf(userId);
				record(adminId, AuditAction.DISABLE, AuditTargetType.USER, target, reason, before);
				outbox.write("user.disabled", userId, actor(adminId), userEvent(target, reason));
			}
			case CUSTOMER -> {
				Customer customer = target.requireCustomer();
				if (customer.isBanned()) {
					return unchangedBan(target, scope, customer.getBanReason(), reason, CustomerStatus.BANNED);
				}
				if (hasPaidOrder == null) {
					return null;
				}
				customer.ban(reason, hasPaidOrder);
				user.bumpAuthzVersion();
				record(adminId, AuditAction.BAN, AuditTargetType.CUSTOMER, target, reason, before);
				outbox.write("customer.banned", userId, actor(adminId), customerEvent(target));
			}
			case SELLER -> {
				Seller seller = target.requireSeller();
				if (seller.getStatus() == SellerStatus.BANNED) {
					return unchangedBan(target, scope, seller.getBanReason(), reason, SellerStatus.BANNED);
				}
				seller.ban(reason);
				user.bumpAuthzVersion();
				record(adminId, AuditAction.BAN, AuditTargetType.SELLER, target, reason, before);
				outbox.write("seller.banned", userId, actor(adminId), sellerEvent(target, reason));
			}
		}
		return BanResult.of(target, scope);
	}

	private UnbanResult applyUnban(UUID adminId, UUID userId, ModerationScope scope, String reason) {
		Target target = lock(userId);
		User user = target.user();
		AuditState before = target.state();
		switch (scope) {
			case USER -> {
				if (user.isActive()) {
					return UnbanResult.of(target, scope);
				}
				user.enable();
				record(adminId, AuditAction.ENABLE, AuditTargetType.USER, target, reason, before);
				outbox.write("user.enabled", userId, actor(adminId), userEvent(target, reason));
			}
			case CUSTOMER -> {
				Customer customer = target.requireCustomer();
				if (!customer.isBanned()) {
					return UnbanResult.of(target, scope);
				}
				customer.unban();
				user.bumpAuthzVersion();
				record(adminId, AuditAction.UNBAN, AuditTargetType.CUSTOMER, target, reason, before);
				outbox.write("customer.unbanned", userId, actor(adminId), customerEvent(target));
			}
			case SELLER -> {
				// Only the ban is lifted: approval and the SELLER role are untouched, so an unapproved seller
				// gains nothing.
				Seller seller = target.requireSeller();
				if (seller.getStatus() == SellerStatus.ACTIVE) {
					return UnbanResult.of(target, scope);
				}
				seller.unban();
				user.bumpAuthzVersion();
				record(adminId, AuditAction.UNBAN, AuditTargetType.SELLER, target, reason, before);
				outbox.write("seller.unbanned", userId, actor(adminId), sellerEvent(target, reason));
			}
		}
		return UnbanResult.of(target, scope);
	}

	private Target lock(UUID userId) {
		User user = users.findLockedById(userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
		return new Target(user, customers.findById(userId).orElse(null), sellers.findById(userId).orElse(null));
	}

	/** Already banned in this scope: the same reason is a no-op, another reason would overwrite history. */
	private static BanResult unchangedBan(Target target, ModerationScope scope, String currentReason, String reason,
			Enum<?> currentStatus) {
		if (!reason.equals(currentReason)) {
			throw ApiException.currentStatus(ErrorCode.INVALID_STATE_TRANSITION, currentStatus);
		}
		return BanResult.of(target, scope);
	}

	private void record(UUID adminId, AuditAction action, AuditTargetType type, Target target, String reason,
			AuditState before) {
		audit.record(adminId, action, type, target.user().getUserId(), reason, before, target.state());
	}

	private static EventActor actor(UUID adminId) {
		return EventActor.user(adminId, EventActor.Context.ADMIN);
	}

	private static Map<String, Object> userEvent(Target target, String reason) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("userId", target.user().getUserId());
		payload.put("authzVersion", target.user().getAuthzVersion());
		payload.put("reason", reason);
		payload.put("hasSellerProfile", target.seller() != null);
		return payload;
	}

	private static Map<String, Object> customerEvent(Target target) {
		Customer customer = target.customer();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("userId", target.user().getUserId());
		payload.put("customerId", customer.getCustomerId());
		payload.put("banMode", customer.getBanMode());
		payload.put("hasPaidOrderBeforeBan", customer.getHasPaidOrderBeforeBan());
		payload.put("authzVersion", target.user().getAuthzVersion());
		return payload;
	}

	private static Map<String, Object> sellerEvent(Target target, String reason) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("userId", target.user().getUserId());
		payload.put("sellerId", target.seller().getSellerId());
		payload.put("status", target.seller().getStatus());
		payload.put("authzVersion", target.user().getAuthzVersion());
		payload.put("reason", reason);
		return payload;
	}

	private record Target(User user, Customer customer, Seller seller) {

		AuditState state() {
			return AuditState.capture(user, customer, seller);
		}

		Customer requireCustomer() {
			if (customer == null) {
				throw new ApiException(ErrorCode.NOT_FOUND, "Customer profile not found");
			}
			return customer;
		}

		Seller requireSeller() {
			if (seller == null) {
				throw new ApiException(ErrorCode.NOT_FOUND, "Seller profile not found");
			}
			return seller;
		}

	}

	public record BanResult(UUID userId, ModerationScope scope, UserStatus userStatus, CustomerStatus customerStatus,
			SellerStatus sellerStatus, CustomerBanMode banMode, long authzVersion) {

		static BanResult of(Target target, ModerationScope scope) {
			Customer customer = target.customer();
			return new BanResult(target.user().getUserId(), scope, target.user().getStatus(),
					customer == null ? null : customer.getStatus(),
					target.seller() == null ? null : target.seller().getStatus(),
					customer == null ? null : customer.getBanMode(), target.user().getAuthzVersion());
		}

	}

	public record UnbanResult(UUID userId, ModerationScope scope, UserStatus userStatus,
			CustomerStatus customerStatus, SellerStatus sellerStatus, long authzVersion) {

		static UnbanResult of(Target target, ModerationScope scope) {
			return new UnbanResult(target.user().getUserId(), scope, target.user().getStatus(),
					target.customer() == null ? null : target.customer().getStatus(),
					target.seller() == null ? null : target.seller().getStatus(), target.user().getAuthzVersion());
		}

	}

}
