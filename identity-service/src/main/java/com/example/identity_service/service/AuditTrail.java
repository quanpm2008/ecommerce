package com.example.identity_service.service;

import com.example.identity_service.dto.AuditState;
import com.example.identity_service.entity.AdminAuditLog;
import com.example.identity_service.entity.AuditAction;
import com.example.identity_service.entity.AuditTargetType;
import com.example.identity_service.repository.AdminAuditLogRepository;
import com.example.identity_service.config.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Writes {@code admin_audit_log} rows for admin actions, in the caller's transaction so the row exists exactly when
 * the change does. The request id comes from the current request ({@link RequestIdFilter}).
 */
@Component
@RequiredArgsConstructor
public class AuditTrail {

	private final AdminAuditLogRepository log;

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(UUID actorUserId, AuditAction action, AuditTargetType targetType, UUID targetId, String reason,
			AuditState before, AuditState after) {
		log.save(new AdminAuditLog(actorUserId, action, targetType, targetId, reason,
				Map.of("before", before.asMap(), "after", after.asMap()), MDC.get(RequestIdFilter.MDC_KEY)));
	}

}
