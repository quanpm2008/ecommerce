package com.example.identity_service.rbac;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Seeded by migration only (DB §3.1.6).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "permissions")
public class Permission {

	@Id
	private UUID permissionId;

	private String code;

	private String description;

	private Instant createdAt;

}
