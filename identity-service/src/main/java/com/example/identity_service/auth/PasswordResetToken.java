package com.example.identity_service.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Single-use password reset link (DB §6). Only the token's hash is stored.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "password_reset_tokens")
class PasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID tokenId;

	private UUID userId;

	private String tokenHash;

	private Instant expiresAt;

	private Instant usedAt;

	@CreationTimestamp
	private Instant createdAt;

	PasswordResetToken(UUID userId, String tokenHash, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	boolean isUsable(Instant now) {
		return usedAt == null && now.isBefore(expiresAt);
	}

	void markUsed(Instant now) {
		usedAt = now;
	}

}
