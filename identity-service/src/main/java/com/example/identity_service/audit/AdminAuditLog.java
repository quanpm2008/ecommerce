package com.example.identity_service.audit;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One audited admin or system action on a user or profile (DB §6). {@code beforeAfter} must never contain tokens or
 * passwords.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "admin_audit_log")
public class AdminAuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID auditId;

	private UUID actorUserId;

	@Enumerated(EnumType.STRING)
	private AuditAction action;

	@Enumerated(EnumType.STRING)
	private AuditTargetType targetType;

	private UUID targetId;

	private String reason;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> beforeAfter;

	private String requestId;

	@CreationTimestamp
	private Instant happenedAt;

	/** {@code actorUserId} is null for system actions such as the admin bootstrap. */
	public AdminAuditLog(UUID actorUserId, AuditAction action, AuditTargetType targetType, UUID targetId,
			String reason, Map<String, Object> beforeAfter, String requestId) {
		this.actorUserId = actorUserId;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.reason = reason;
		this.beforeAfter = beforeAfter;
		this.requestId = requestId;
	}

}
