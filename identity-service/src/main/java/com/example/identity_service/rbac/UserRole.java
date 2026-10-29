package com.example.identity_service.rbac;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A role held by a user (DB §3.1.7). Revoking sets {@code revokedAt}; granting again reuses the same row. Every grant
 * or revoke must bump the user's {@code authzVersion} and be audited in the same transaction.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_roles")
public class UserRole {

	@EmbeddedId
	private UserRoleId id;

	@MapsId("roleId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "role_id")
	private Role role;

	private UUID grantedByUserId;

	private Instant grantedAt;

	private Instant revokedAt;

	/** {@code grantedByUserId} is null for bootstrap and system grants (registration, approval by the system). */
	public UserRole(UUID userId, Role role, UUID grantedByUserId) {
		this.id = new UserRoleId(userId, role.getRoleId());
		this.role = role;
		this.grantedByUserId = grantedByUserId;
		this.grantedAt = Instant.now();
	}

	public boolean isActive() {
		return revokedAt == null;
	}

	public void revoke() {
		revokedAt = Instant.now();
	}

}
