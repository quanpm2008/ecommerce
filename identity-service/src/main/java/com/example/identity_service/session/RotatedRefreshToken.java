package com.example.identity_service.session;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Hash of a refresh token that was already exchanged; seeing it again revokes its session.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "rotated_refresh_tokens")
class RotatedRefreshToken {

	@Id
	private String tokenHash;

	private UUID sessionId;

	private Instant rotatedAt;

	RotatedRefreshToken(String tokenHash, UUID sessionId, Instant rotatedAt) {
		this.tokenHash = tokenHash;
		this.sessionId = sessionId;
		this.rotatedAt = rotatedAt;
	}

}
