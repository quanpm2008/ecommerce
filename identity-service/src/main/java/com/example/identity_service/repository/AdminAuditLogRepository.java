package com.example.identity_service.repository;

import com.example.identity_service.entity.AdminAuditLog;
import com.example.identity_service.entity.AuditTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

	List<AdminAuditLog> findByTargetTypeAndTargetIdOrderByHappenedAt(AuditTargetType targetType, UUID targetId);

}
