package com.example.identity_service.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

	List<AdminAuditLog> findByTargetTypeAndTargetIdOrderByHappenedAt(AuditTargetType targetType, UUID targetId);

}
