package com.example.identity_service.service;

import com.example.identity_service.config.AdminBootstrapProperties;
import com.example.identity_service.entity.AdminAuditLog;
import com.example.identity_service.repository.AdminAuditLogRepository;
import com.example.identity_service.entity.AuditAction;
import com.example.identity_service.entity.AuditTargetType;
import com.example.identity_service.entity.RoleCode;
import com.example.identity_service.entity.AccountType;
import com.example.identity_service.entity.User;
import com.example.identity_service.repository.UserRepository;
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
 * Creates the configured admin (an ADMIN account, LOCAL, with the ADMIN role) at startup; ADMIN is never granted through a public endpoint
 * (DB §3.1). An email that already belongs to a user is left untouched, so the bootstrap never escalates an existing
 * account.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

	private final AdminBootstrapProperties properties;

	private final UserRepository users;

	private final RoleAssignments roleAssignments;

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
		User admin = users.save(User.local(AccountType.ADMIN, email, passwordEncoder.encode(properties.password()),
				properties.fullName(), null));
		roleAssignments.grant(admin, RoleCode.ADMIN, null);
		auditLog.save(new AdminAuditLog(null, AuditAction.GRANT_ROLE, AuditTargetType.USER, admin.getUserId(),
				"Admin bootstrap", Map.of("after", Map.of("role", RoleCode.ADMIN.name())), null));
		log.info("Admin bootstrap created user {}", admin.getUserId());
	}

}
