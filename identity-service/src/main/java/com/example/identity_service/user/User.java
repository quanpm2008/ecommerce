package com.example.identity_service.user;

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

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * The single identity and credential record (DB §3.1.1). {@code userId} is the JWT {@code sub} and the PK shared by
 * {@code Customer} and {@code Seller}.
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
	private AuthMethod authMethod;

	private String passwordHash;

	private String googleSubject;

	private String fullName;

	private String phoneNumber;

	@Enumerated(EnumType.STRING)
	private UserStatus status;

	private String disabledReason;

	private long authzVersion;

	@CreationTimestamp
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

	/**
	 * A new ACTIVE user that logs in with email and password. {@code phoneNumber} must already be normalized.
	 */
	public static User local(String email, String passwordHash, String fullName, String phoneNumber) {
		User user = new User();
		user.email = normalizeEmail(email);
		user.authMethod = AuthMethod.LOCAL;
		user.passwordHash = Objects.requireNonNull(passwordHash);
		user.fullName = fullName;
		user.phoneNumber = phoneNumber;
		user.status = UserStatus.ACTIVE;
		user.authzVersion = 1;
		return user;
	}

	/** Emails are stored trimmed and lowercase; the database rejects anything else. */
	public static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	public void rename(String fullName) {
		this.fullName = Objects.requireNonNull(fullName);
	}

	/** {@code phoneNumber} must already be normalized. */
	public void changePhoneNumber(String phoneNumber) {
		this.phoneNumber = Objects.requireNonNull(phoneNumber);
	}

	/** {@code passwordHash} is already encoded. Callers revoke the user's sessions. */
	public void changePassword(String passwordHash) {
		if (authMethod != AuthMethod.LOCAL) {
			throw new IllegalStateException("Only LOCAL users have a password");
		}
		this.passwordHash = Objects.requireNonNull(passwordHash);
	}

	public void disable(String reason) {
		status = UserStatus.DISABLED;
		disabledReason = Objects.requireNonNull(reason);
		bumpAuthzVersion();
	}

	public void enable() {
		status = UserStatus.ACTIVE;
		disabledReason = null;
		bumpAuthzVersion();
	}

	/** Invalidates permissions carried by tokens issued before a role or access-status change. */
	public void bumpAuthzVersion() {
		authzVersion++;
	}

	public boolean isActive() {
		return status == UserStatus.ACTIVE;
	}

}
