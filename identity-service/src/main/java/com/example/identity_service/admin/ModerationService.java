package com.example.identity_service.admin;

import com.example.identity_service.audit.AuditAction;
import com.example.identity_service.audit.AuditState;
import com.example.identity_service.audit.AuditTargetType;
import com.example.identity_service.audit.AuditTrail;
import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.common.web.StrictBody;
import com.example.identity_service.customer.Customer;
import com.example.identity_service.customer.CustomerRepository;
import com.example.identity_service.customer.CustomerStatus;
import com.example.identity_service.obligation.AccountObligations;
import com.example.identity_service.obligation.ObligationSource;
import com.example.identity_service.outbox.EventActor;
import com.example.identity_service.outbox.OutboxWriter;
import com.example.identity_service.seller.Seller;
import com.example.identity_service.seller.SellerApprovalStatus;
import com.example.identity_service.seller.SellerRepository;
import com.example.identity_service.seller.SellerStatus;
import com.example.identity_service.session.SessionService;
import com.example.identity_service.user.AccessMode;
import com.example.identity_service.user.AccountStatus;
import com.example.identity_service.user.AccountType;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import com.example.identity_service.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The one ban lifecycle of a customer or seller account (G-14, UC-E03, API §3.2, §6.1 ban/unban, §7.3 J7; Thẻ 15). No
 * scope: a ban acts on the chosen account only, never on other accounts of the same person, never on an ADMIN
 * account and never on a seller that is not APPROVED.
 * <ul>
 * <li>Ban: the account's current obligations (Order, and Finance for a seller) are read outside any transaction; open
 * obligations → BANNED with a limited session ({@link AccessMode}); every one verified gone → DISABLED, sessions
 * revoked. A source that cannot answer → 503 and nothing is written.</li>
 * <li>Reconcile (J7, and on every login and refresh): BANNED with nothing left → DISABLED; DISABLED with a late
 * obligation → BANNED again (limited session), never ACTIVE. The episode's timestamps stay.</li>
 * <li>Unban: BANNED at any time, DISABLED until {@code restoreUntil} (409 {@code ACCOUNT_RESTORE_WINDOW_EXPIRED}
 * after); needs no obligations.</li>
 * </ul>
 * Each change commits the status pair, timestamps, {@code authzVersion}, the audit row and the outbox events in one
 * Identity transaction. A change that read obligations commits only if the account's {@code authzVersion} is still the
 * one it read, so a reconciliation never overwrites an admin's unban.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationService {

	static final Set<String> BAN_FIELDS = Set.of("reason");

	private static final int UNBAN_REASON_MAX = 1000;

	private static final EventActor SYSTEM = new EventActor(null, EventActor.Kind.SERVICE, EventActor.Context.ACCOUNT);

	private final UserRepository users;

	private final CustomerRepository customers;

	private final SellerRepository sellers;

	private final SessionService sessions;

	private final ObligationSource obligations;

	private final AuditTrail audit;

	private final OutboxWriter outbox;

	private final TransactionTemplate tx;

	private final Clock clock;

	/** {@code POST /admin/ban/{userId}}; body {@code {reason}} only. */
	public BanResult ban(UUID adminId, UUID userId, Map<String, Object> body) {
		String rawReason = StrictBody.of(body, BAN_FIELDS).text("reason", 2000);
		Snapshot before = tx.execute(status -> {
			Target target = target(userId, false);
			target.requireBannable();
			return new Snapshot(target.user(), target.user().getAuthzVersion());
		});
		String reason = AdminReasons.check(rawReason, before.user().getAccountType() == AccountType.SELLER
				? AdminReasons.SELLER_BAN : AdminReasons.CUSTOMER_BAN);
		AccountObligations current = obligations.of(before.user());
		return tx.execute(status -> {
			Target target = target(userId, true);
			target.requireBannable();
			requireUnchanged(target.user(), before.version());
			if (target.profileBanned()) {
				return result(target, current); // a repeated ban changes nothing and announces nothing
			}
			User user = target.user();
			AuditState stateBefore = capture(target);
			Instant now = clock.instant();
			target.ban(reason);
			if (current.any()) {
				user.startBan(now, current.accessMode(user.getAccountType()));
			}
			else {
				user.startBan(now, AccessMode.LOGIN_BLOCKED);
				user.disableForBan(reason, now);
				sessions.revokeAllOf(user.getUserId());
			}
			EventActor admin = EventActor.user(adminId, EventActor.Context.ADMIN);
			outbox.write(target.eventPrefix() + ".banned", user.getUserId(), admin, profilePayload(target, reason));
			if (!user.isActive()) {
				outbox.write("user.disabled", user.getUserId(), admin, userPayload(target, reason));
			}
			audit.record(adminId, AuditAction.BAN, target.auditType(), user.getUserId(), reason, stateBefore,
					capture(target));
			return result(target, current);
		});
	}

	/** {@code POST /admin/unban/{userId}}; body {@code {reason?}} only. */
	public BanResult unban(UUID adminId, UUID userId, Map<String, Object> body) {
		StrictBody fields = StrictBody.of(body, BAN_FIELDS);
		String reason = fields.has("reason") ? fields.nullableText("reason", UNBAN_REASON_MAX) : null;
		String auditReason = reason == null || reason.isBlank() ? null : reason.strip();
		return tx.execute(status -> {
			Target target = target(userId, true);
			target.requireBannable();
			if (!target.profileBanned()) {
				return result(target, null); // already ACTIVE: idempotent, nothing announced
			}
			User user = target.user();
			Instant now = clock.instant();
			if (!user.isActive() && !user.restorableAt(now)) {
				throw new ApiException(ErrorCode.ACCOUNT_RESTORE_WINDOW_EXPIRED);
			}
			boolean wasDisabled = !user.isActive();
			AuditState stateBefore = capture(target);
			target.unban();
			user.liftBan();
			EventActor admin = EventActor.user(adminId, EventActor.Context.ADMIN);
			outbox.write(target.eventPrefix() + ".unbanned", user.getUserId(), admin,
					profilePayload(target, auditReason));
			if (wasDisabled) {
				outbox.write("user.enabled", user.getUserId(), admin, userPayload(target, auditReason));
			}
			audit.record(adminId, AuditAction.UNBAN, target.auditType(), user.getUserId(), auditReason, stateBefore,
					capture(target));
			return result(target, null);
		});
	}

	/**
	 * Brings a banned account in line with its obligations (J7, login, refresh). Not banned → nothing. Throws
	 * {@code ApiException(SERVICE_UNAVAILABLE)} when a source cannot answer (nothing changed); returns false when the
	 * account changed meanwhile (the change wins, nothing written here).
	 */
	public boolean reconcile(UUID userId) {
		Snapshot before = tx.execute(status -> {
			Target target = target(userId, false);
			return target.lifecycleApplies() && target.profileBanned()
					? new Snapshot(target.user(), target.user().getAuthzVersion()) : null;
		});
		if (before == null) {
			return true;
		}
		AccountObligations current = obligations.of(before.user());
		Boolean applied = tx.execute(status -> {
			Target target = target(userId, true);
			if (target.user().getAuthzVersion() != before.version() || !target.profileBanned()) {
				return false;
			}
			apply(target, current);
			return true;
		});
		return Boolean.TRUE.equals(applied);
	}

	/** What a ban would do now ({@code BanImpact}, API §6.1); null when it does not apply or a source cannot answer. */
	public BanImpact banImpact(User account) {
		if (account.getAccountType() == AccountType.ADMIN) {
			return null;
		}
		try {
			AccountObligations current = obligations.of(account);
			return new BanImpact(current.evaluatedAt(),
					current.any() ? AccountStatus.BANNED : AccountStatus.DISABLED, current);
		}
		catch (ApiException unavailable) {
			return null;
		}
	}

	private void apply(Target target, AccountObligations current) {
		User user = target.user();
		AuditState stateBefore = capture(target);
		if (current.any()) {
			AccessMode mode = current.accessMode(user.getAccountType());
			if (!user.isActive()) {
				user.reopenForObligations(mode);
				outbox.write("user.enabled", user.getUserId(), SYSTEM, userPayload(target, target.banReason()));
				audit.record(null, AuditAction.ENABLE, AuditTargetType.USER, user.getUserId(),
						"Late obligation reopened a limited session", stateBefore, capture(target));
			}
			else {
				user.changeAccessMode(mode);
			}
			return;
		}
		if (user.isActive()) {
			user.disableForBan(target.banReason(), clock.instant());
			sessions.revokeAllOf(user.getUserId());
			outbox.write("user.disabled", user.getUserId(), SYSTEM, userPayload(target, target.banReason()));
			audit.record(null, AuditAction.DISABLE, AuditTargetType.USER, user.getUserId(),
					"Every obligation verified gone", stateBefore, capture(target));
		}
	}

	private static void requireUnchanged(User user, long version) {
		if (user.getAuthzVersion() != version) {
			throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
					"The account changed while its obligations were read; try again");
		}
	}

	private Target target(UUID userId, boolean lock) {
		User user = (lock ? users.findLockedById(userId) : users.findById(userId))
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
		return new Target(user, customers.findById(userId).orElse(null), sellers.findById(userId).orElse(null));
	}

	private AuditState capture(Target target) {
		return AuditState.capture(target.user(), target.customer(), target.seller());
	}

	private BanResult result(Target target, AccountObligations current) {
		User user = target.user();
		return new BanResult(user.getUserId(), user.getAccountType(), target.accountStatus(), user.getStatus(),
				target.customer() == null ? null : target.customer().getStatus(),
				target.seller() == null ? null : target.seller().getStatus(), user.getAccessMode(), user.getBannedAt(),
				user.getDisabledAt(), user.getRestoreUntil(), user.getAuthzVersion(), current);
	}

	/** {@code seller.banned/unbanned} and {@code customer.banned/unbanned} (API §7.2). */
	private static Map<String, Object> profilePayload(Target target, String reason) {
		User user = target.user();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("userId", user.getUserId());
		if (target.seller() != null) {
			payload.put("sellerId", user.getUserId());
			payload.put("status", target.seller().getStatus());
		}
		else {
			payload.put("customerId", user.getUserId());
		}
		putAccess(payload, target);
		payload.put("reason", reason);
		return payload;
	}

	/** {@code user.disabled/enabled} (API §7.2); {@code user.enabled} with BANNED is a late obligation, not an unban. */
	private static Map<String, Object> userPayload(Target target, String reason) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("userId", target.user().getUserId());
		payload.put("accountType", target.user().getAccountType());
		putAccess(payload, target);
		payload.put("reason", reason);
		return payload;
	}

	private static void putAccess(Map<String, Object> payload, Target target) {
		User user = target.user();
		payload.put("accountStatus", target.accountStatus());
		payload.put("accessMode", user.getAccessMode());
		payload.put("bannedAt", user.getBannedAt());
		payload.put("disabledAt", user.getDisabledAt());
		payload.put("restoreUntil", user.getRestoreUntil());
		payload.put("authzVersion", user.getAuthzVersion());
	}

	private record Snapshot(User user, long version) {
	}

	/** The account and its one profile (Customer or Seller, never both). */
	private record Target(User user, Customer customer, Seller seller) {

		boolean lifecycleApplies() {
			return user.getAccountType() != AccountType.ADMIN && (customer != null || seller != null);
		}

		void requireBannable() {
			if (user.getAccountType() == AccountType.ADMIN) {
				throw new ApiException(ErrorCode.FORBIDDEN, "Admin accounts cannot be banned");
			}
			if (seller != null && seller.getApprovalStatus() != SellerApprovalStatus.APPROVED) {
				throw ApiException.currentStatus(ErrorCode.INVALID_STATE_TRANSITION, seller.getApprovalStatus());
			}
			if (customer == null && seller == null) {
				throw new ApiException(ErrorCode.NOT_FOUND, "User not found");
			}
		}

		boolean profileBanned() {
			return customer != null ? customer.getStatus() == CustomerStatus.BANNED
					: seller != null && seller.getStatus() == SellerStatus.BANNED;
		}

		AccountStatus accountStatus() {
			return AccountStatus.of(user, profileBanned());
		}

		String banReason() {
			return customer != null ? customer.getBanReason() : seller.getBanReason();
		}

		void ban(String reason) {
			if (customer != null) {
				customer.ban(reason);
			}
			else {
				seller.ban(reason);
			}
		}

		void unban() {
			if (customer != null) {
				customer.unban();
			}
			else {
				seller.unban();
			}
		}

		String eventPrefix() {
			return customer != null ? "customer" : "seller";
		}

		AuditTargetType auditType() {
			return customer != null ? AuditTargetType.CUSTOMER : AuditTargetType.SELLER;
		}

	}

	/** API §6.1 ban/unban 200 body; {@code obligations} only on ban. */
	public record BanResult(UUID userId, AccountType accountType, AccountStatus accountStatus, UserStatus userStatus,
			CustomerStatus customerStatus, SellerStatus sellerStatus, AccessMode accessMode, Instant bannedAt,
			Instant disabledAt, Instant restoreUntil, long authzVersion, AccountObligations obligations) {
	}

	/** API §6.1 {@code BanImpact}: what a ban would do now. */
	public record BanImpact(Instant evaluatedAt, AccountStatus resultingAccountStatus, AccountObligations obligations) {
	}

}
