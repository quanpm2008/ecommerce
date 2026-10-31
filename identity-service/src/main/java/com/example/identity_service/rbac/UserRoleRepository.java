package com.example.identity_service.rbac;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

	@Query("select ur.role.code from UserRole ur where ur.id.userId = :userId and ur.revokedAt is null")
	Set<RoleCode> findActiveRoleCodes(UUID userId);

	@Query("""
			select distinct p.code from UserRole ur join ur.role r join r.permissions p
			where ur.id.userId = :userId and ur.revokedAt is null""")
	Set<String> findActivePermissionCodes(UUID userId);

	/** True if the user held the role and it was revoked (only admins revoke roles). */
	@Query("select count(ur) > 0 from UserRole ur where ur.id.userId = :userId and ur.role.code = :code and ur.revokedAt is not null")
	boolean wasRevoked(UUID userId, RoleCode code);

}
