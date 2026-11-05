package com.example.identity_service.bootstrap;

import com.example.identity_service.TestcontainersConfiguration;
import com.example.identity_service.audit.AdminAuditLog;
import com.example.identity_service.audit.AdminAuditLogRepository;
import com.example.identity_service.audit.AuditAction;
import com.example.identity_service.audit.AuditTargetType;
import com.example.identity_service.rbac.PermissionResolver;
import com.example.identity_service.rbac.Permissions;
import com.example.identity_service.rbac.RoleCode;
import com.example.identity_service.rbac.RoleAssignments;
import com.example.identity_service.user.AuthMethod;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import com.example.identity_service.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = { "eureka.client.enabled=false", "identity.bootstrap.admin.email=Admin@Example.test",
		"identity.bootstrap.admin.password=Admin1234" })
@Import(TestcontainersConfiguration.class)
class AdminBootstrapTests {

	@Autowired
	AdminBootstrap bootstrap;

	@Autowired
	UserRepository users;

	@Autowired
	RoleAssignments roleAssignments;

	@Autowired
	AdminAuditLogRepository auditLog;

	@Autowired
	PermissionResolver resolver;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	TransactionTemplate tx;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void startupCreatesLocalAdminWithOnlyTheAdminRole() {
		User admin = users.findByEmail("admin@example.test").orElseThrow();

		assertThat(admin.getAuthMethod()).isEqualTo(AuthMethod.LOCAL);
		assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(admin.getFullName()).isEqualTo("Administrator");
		assertThat(admin.getAuthzVersion()).as("the ADMIN grant bumps the version").isEqualTo(2);
		assertThat(passwordEncoder.matches("Admin1234", admin.getPasswordHash())).isTrue();
		assertThat(resolver.activeRolesOf(admin)).containsExactly(RoleCode.ADMIN);
		assertThat(resolver.permissionsOf(admin)).contains("seller:approve", "account:moderate")
				.containsAll(Permissions.BASELINE)
				.doesNotContain("cart:manage:own", "product:manage:own");
		assertThat(jdbc.queryForObject("select count(*) from customers where customer_id = ?", Integer.class,
				admin.getUserId())).isZero();
		assertThat(jdbc.queryForObject("select count(*) from sellers where seller_id = ?", Integer.class,
				admin.getUserId())).isZero();

		List<AdminAuditLog> audit = auditLog.findByTargetTypeAndTargetIdOrderByHappenedAt(AuditTargetType.USER,
				admin.getUserId());
		assertThat(audit).singleElement().satisfies(entry -> {
			assertThat(entry.getAction()).isEqualTo(AuditAction.GRANT_ROLE);
			assertThat(entry.getActorUserId()).isNull();
			assertThat(entry.getBeforeAfter()).containsEntry("after", Map.of("role", "ADMIN"));
		});
	}

	@Test
	void runningAgainChangesNothing() {
		bootstrap.run(null);

		User admin = users.findByEmail("admin@example.test").orElseThrow();
		assertThat(jdbc.queryForObject("select count(*) from users where email = 'admin@example.test'", Integer.class))
				.isEqualTo(1);
		assertThat(auditLog.findByTargetTypeAndTargetIdOrderByHappenedAt(AuditTargetType.USER, admin.getUserId()))
				.hasSize(1);
	}

	@Test
	void existingUserWithTheConfiguredEmailIsNotEscalated() {
		User existing = users.save(User.local("plain@example.test", "{noop}x", "Plain User", null));
		AdminBootstrap other = withProperties(new AdminBootstrapProperties("plain@example.test", "Admin1234", "Admin"));

		tx.executeWithoutResult(status -> other.run(null));

		assertThat(resolver.activeRolesOf(existing)).isEmpty();
	}

	@Test
	void weakPasswordFailsStartup() {
		AdminBootstrap weak = withProperties(new AdminBootstrapProperties("weak@example.test", "password", "Admin"));

		assertThatThrownBy(() -> weak.run(null)).isInstanceOf(IllegalStateException.class);
		assertThat(users.findByEmail("weak@example.test")).isEmpty();
	}

	private AdminBootstrap withProperties(AdminBootstrapProperties properties) {
		return new AdminBootstrap(properties, users, roleAssignments, auditLog, passwordEncoder);
	}

}
