package com.example.identity_service.bootstrap;

import com.example.identity_service.audit.AdminAuditLog;
import com.example.identity_service.audit.AdminAuditLogRepository;
import com.example.identity_service.audit.AuditAction;
import com.example.identity_service.audit.AuditTargetType;
import com.example.identity_service.rbac.Role;
import com.example.identity_service.rbac.RoleCode;
import com.example.identity_service.rbac.RoleRepository;
import com.example.identity_service.rbac.UserRole;
import com.example.identity_service.rbac.UserRoleRepository;
import com.example.identity_service.user.PasswordPolicy;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Creates the configured admin (User LOCAL + ADMIN role) at startup; ADMIN is never granted through a public endpoint
 * (DB §3.1). An email that already belongs to a user is left untouched, so the bootstrap never escalates an existing
 * account.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

	private final AdminBootstrapProperties properties;

	private final UserRepository users;

	private final RoleRepository roles;

	private final UserRoleRepository userRoles;

	private final AdminAuditLogRepository auditLog;

	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!StringUtils.hasText(properties.email())) {
			return;
		}
		if (!PasswordPolicy.isSatisfiedBy(properties.password())) {
			throw new IllegalStateException(
					"identity.bootstrap.admin.password must have at least 8 characters with an uppercase letter, "
							+ "a lowercase letter and a digit (at most 72 bytes)");
		}
		String email = User.normalizeEmail(properties.email());
		if (users.findByEmail(email).isPresent()) {
			log.info("Admin bootstrap skipped: a user with the configured email already exists");
			return;
		}
		User admin = users.save(User.local(email, passwordEncoder.encode(properties.password()),
				properties.fullName(), null));
		Role adminRole = roles.findByCode(RoleCode.ADMIN).orElseThrow();
		userRoles.save(new UserRole(admin.getUserId(), adminRole, null));
		auditLog.save(new AdminAuditLog(null, AuditAction.GRANT_ROLE, AuditTargetType.USER, admin.getUserId(),
				"Admin bootstrap", Map.of("after", Map.of("role", RoleCode.ADMIN.name())), null));
		log.info("Admin bootstrap created user {}", admin.getUserId());
	}

}
