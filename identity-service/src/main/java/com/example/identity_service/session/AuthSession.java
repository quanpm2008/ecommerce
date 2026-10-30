package com.example.identity_service.session;

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
 * One login on one device (DB §6). The access token carries {@code sessionId}; revoking the session ends both its
 * refresh token and every access token issued for it. {@code expiresAt} is the current refresh token's expiry and
 * moves forward on each rotation.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "auth_sessions")
public class AuthSession {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID sessionId;

	private UUID userId;

	private String refreshTokenHash;

	private Instant expiresAt;

	private Instant revokedAt;

	@CreationTimestamp
	private Instant createdAt;

	AuthSession(UUID userId, String refreshTokenHash, Instant expiresAt) {
		this.userId = userId;
		this.refreshTokenHash = refreshTokenHash;
		this.expiresAt = expiresAt;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	boolean canRefresh(Instant now) {
		return !isRevoked() && now.isBefore(expiresAt);
	}

	void rotate(String newRefreshTokenHash, Instant newExpiresAt) {
		refreshTokenHash = newRefreshTokenHash;
		expiresAt = newExpiresAt;
	}

	void revoke(Instant now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}

}
