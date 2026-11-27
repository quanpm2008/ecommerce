package com.example.identity_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * One account and its credentials (DB §3.1.1). {@code userId} is the JWT {@code sub} and the PK of the account's
 * {@code Customer} or {@code Seller} profile (never both). {@code accountType} never changes.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID userId;

	private String email;

	@Enumerated(EnumType.STRING)
	private AccountType accountType;

	@Enumerated(EnumType.STRING)
	private AuthMethod authMethod;

	private String passwordHash;

	private String googleSubject;

	private String fullName;

	private String phoneNumber;

	/** The account's avatar; for a seller it is also the shop logo (DB §3.1.1). */
	private String avatarObjectKey;

	@Enumerated(EnumType.STRING)
	private UserStatus status;

	private String disabledReason;

	/** Start of the current ban episode (G-14); null when not banned. */
	private Instant bannedAt;

	/** First time the current episode went DISABLED; kept if it goes BANNED and DISABLED again. */
	private Instant disabledAt;

	/** {@code disabledAt} + 30 days: the last moment an admin may lift the ban. */
	private Instant restoreUntil;

	@Enumerated(EnumType.STRING)
	private AccessMode accessMode;

	private long authzVersion;

	/** The current refresh session (decision 0003): all three set, or all three null. Only the token's hash. */
	private String refreshTokenHash;

	private Instant refreshTokenExpiresAt;

	/** The {@code sessionId} claim of this session's access tokens. */
	private UUID sessionId;

	@CreationTimestamp
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

	/**
	 * A new ACTIVE account that logs in with email and password. {@code phoneNumber} must already be normalized.
	 */
	public static User local(AccountType accountType, String email, String passwordHash, String fullName,
			String phoneNumber) {
		User user = new User();
		user.accountType = Objects.requireNonNull(accountType);
		user.email = normalizeEmail(email);
		user.authMethod = AuthMethod.LOCAL;
		user.passwordHash = Objects.requireNonNull(passwordHash);
		user.fullName = fullName;
		user.phoneNumber = phoneNumber;
		user.status = UserStatus.ACTIVE;
		user.accessMode = AccessMode.FULL;
		user.authzVersion = 1;
		return user;
	}

	/**
	 * A new ACTIVE customer account that signs in with Google (DB §3.1.1: GOOGLE is for customers only, no password,
	 * no phone until the customer adds one).
	 */
	public static User google(String email, String googleSubject, String fullName) {
		User user = new User();
		user.accountType = AccountType.CUSTOMER;
		user.email = normalizeEmail(email);
		user.authMethod = AuthMethod.GOOGLE;
		user.googleSubject = Objects.requireNonNull(googleSubject);
		user.fullName = Objects.requireNonNull(fullName);
		user.status = UserStatus.ACTIVE;
		user.accessMode = AccessMode.FULL;
		user.authzVersion = 1;
		return user;
	}

	/**
	 * Replaces any current session with a new one (one session per user, decision 0003) and returns its id. The
	 * caller holds the row lock.
	 */
	public UUID startSession(String refreshTokenHash, Instant expiresAt) {
		this.sessionId = UUID.randomUUID();
		this.refreshTokenHash = Objects.requireNonNull(refreshTokenHash);
		this.refreshTokenExpiresAt = Objects.requireNonNull(expiresAt);
		return sessionId;
	}

	/** Refresh: a new token hash and expiry for the same session. */
	public void rotateRefreshToken(String refreshTokenHash, Instant expiresAt) {
		if (sessionId == null) {
			throw new IllegalStateException("No session to rotate");
		}
		this.refreshTokenHash = Objects.requireNonNull(refreshTokenHash);
		this.refreshTokenExpiresAt = Objects.requireNonNull(expiresAt);
	}

	/** Ends the current session, if any. */
	public void endSession() {
		this.sessionId = null;
		this.refreshTokenHash = null;
		this.refreshTokenExpiresAt = null;
	}

	/** {@code sessionId} is this user's current session. */
	public boolean hasSession(UUID sessionId) {
		return this.sessionId != null && this.sessionId.equals(sessionId);
	}

	public boolean canRefresh(Instant now) {
		return sessionId != null && now.isBefore(refreshTokenExpiresAt);
	}

	/** Emails are stored trimmed and lowercase; the database rejects anything else. */
	public static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	/** For a seller account this is the shop name (DB §3.1.3); callers check it may change. */
	public void rename(String fullName) {
		this.fullName = Objects.requireNonNull(fullName);
	}

	/** {@code phoneNumber} must already be normalized. For a seller it is also the shop's contact phone. */
	public void changePhoneNumber(String phoneNumber) {
		this.phoneNumber = Objects.requireNonNull(phoneNumber);
	}

	/** {@code avatarObjectKey} must already be verified as this account's upload; null removes the avatar. */
	public void changeAvatar(String avatarObjectKey) {
		this.avatarObjectKey = avatarObjectKey;
	}

	/** {@code passwordHash} is already encoded. Callers revoke the user's sessions. */
	public void changePassword(String passwordHash) {
		if (authMethod != AuthMethod.LOCAL) {
			throw new IllegalStateException("Only LOCAL users have a password");
		}
		this.passwordHash = Objects.requireNonNull(passwordHash);
	}

	/** How long a DISABLED account may still be restored (G-14, Thẻ 15). */
	public static final Duration RESTORE_WINDOW = Duration.ofDays(30);

	/** A new ban episode with open obligations: a limited session of {@code mode}. */
	public void startBan(Instant now, AccessMode mode) {
		this.bannedAt = now;
		this.accessMode = mode;
		bumpAuthzVersion();
	}

	/**
	 * Every obligation is gone: no more sessions. The episode's first DISABLED sets {@code disabledAt} and
	 * {@code restoreUntil}; later ones keep them.
	 */
	public void disableForBan(String reason, Instant now) {
		this.status = UserStatus.DISABLED;
		this.disabledReason = Objects.requireNonNull(reason);
		if (disabledAt == null) {
			this.disabledAt = now;
			this.restoreUntil = now.plus(RESTORE_WINDOW);
		}
		this.accessMode = AccessMode.LOGIN_BLOCKED;
		bumpAuthzVersion();
	}

	/** A late obligation reopens a limited session; still banned, the episode's timestamps are kept. */
	public void reopenForObligations(AccessMode mode) {
		this.status = UserStatus.ACTIVE;
		this.disabledReason = null;
		this.accessMode = mode;
		bumpAuthzVersion();
	}

	/** A banned account's obligations changed; returns whether anything did. */
	public boolean changeAccessMode(AccessMode mode) {
		if (this.accessMode == mode) {
			return false;
		}
		this.accessMode = mode;
		bumpAuthzVersion();
		return true;
	}

	/** The admin lifted the ban: ACTIVE, and the episode's timestamps go. */
	public void liftBan() {
		this.status = UserStatus.ACTIVE;
		this.disabledReason = null;
		this.bannedAt = null;
		this.disabledAt = null;
		this.restoreUntil = null;
		this.accessMode = AccessMode.FULL;
		bumpAuthzVersion();
	}

	/** Whether a DISABLED account may still be lifted at {@code now} (inclusive). */
	public boolean restorableAt(Instant now) {
		return restoreUntil == null || !now.isAfter(restoreUntil);
	}

	/** Invalidates permissions carried by tokens issued before a role or access-status change. */
	public void bumpAuthzVersion() {
		authzVersion++;
	}

	public boolean isActive() {
		return status == UserStatus.ACTIVE;
	}

}
