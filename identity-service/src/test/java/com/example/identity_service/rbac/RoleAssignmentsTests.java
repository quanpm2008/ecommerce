package com.example.identity_service.rbac;

import com.example.identity_service.TestcontainersConfiguration;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "eureka.client.enabled=false")
@Import(TestcontainersConfiguration.class)
@Transactional
class RoleAssignmentsTests {

	@Autowired
	RoleAssignments roleAssignments;

	@Autowired
	PermissionResolver resolver;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void grantAndRevokeEachBumpTheAuthzVersion() {
		User user = newUser();

		assertThat(roleAssignments.grant(user, RoleCode.CUSTOMER, null)).isTrue();
		assertThat(storedVersion(user)).isEqualTo(2);

		assertThat(roleAssignments.revoke(user, RoleCode.CUSTOMER)).isTrue();
		assertThat(storedVersion(user)).isEqualTo(3);
		assertThat(resolver.activeRolesOf(user)).isEmpty();
	}

	@Test
	void changesThatChangeNothingDoNotBump() {
		User user = newUser();
		roleAssignments.grant(user, RoleCode.CUSTOMER, null);

		assertThat(roleAssignments.grant(user, RoleCode.CUSTOMER, null)).isFalse();
		assertThat(roleAssignments.revoke(user, RoleCode.SELLER)).isFalse();
		assertThat(storedVersion(user)).isEqualTo(2);
	}

	@Test
	void grantingARevokedRoleAgainReusesItsRow() {
		User user = newUser();
		User admin = newUser();
		roleAssignments.grant(user, RoleCode.SELLER, null);
		roleAssignments.revoke(user, RoleCode.SELLER);

		roleAssignments.grant(user, RoleCode.SELLER, admin.getUserId());
		users.flush();

		assertThat(jdbc.queryForMap("""
				select count(*) as rows, max(granted_by_user_id::text) as granted_by, bool_and(revoked_at is null) as active
				from user_roles where user_id = ?""", user.getUserId()))
				.containsEntry("rows", 1L)
				.containsEntry("granted_by", admin.getUserId().toString())
				.containsEntry("active", true);
		assertThat(storedVersion(user)).isEqualTo(4);
	}

	@Test
	void everyChangeWritesRolesChangedButOnlyAdminChangesAreAudited() {
		User user = newUser();
		User admin = newUser();

		roleAssignments.grant(user, RoleCode.CUSTOMER, null);
		roleAssignments.grant(user, RoleCode.SELLER, admin.getUserId());
		roleAssignments.revoke(user, RoleCode.CUSTOMER, admin.getUserId());
		roleAssignments.revoke(user, RoleCode.CUSTOMER, admin.getUserId());
		users.flush();

		assertThat(jdbc.queryForList("""
				select payload -> 'payload' ->> 'roleCodes' from outbox_events
				where event_type = 'user.roles_changed' and aggregate_id = ? order by version""", String.class,
				user.getUserId())).containsExactly("[\"CUSTOMER\"]", "[\"CUSTOMER\", \"SELLER\"]", "[\"SELLER\"]");
		assertThat(jdbc.queryForList("""
				select action || ' ' || (before_after -> 'before' ->> 'roles') || ' -> '
				       || (before_after -> 'after' ->> 'roles')
				from admin_audit_log where target_id = ? and actor_user_id = ? order by happened_at, action""", String.class,
				user.getUserId(), admin.getUserId()))
				.containsExactly("GRANT_ROLE [\"CUSTOMER\"] -> [\"CUSTOMER\", \"SELLER\"]",
						"REVOKE_ROLE [\"CUSTOMER\", \"SELLER\"] -> [\"SELLER\"]");
	}

	@Test
	void disableAndEnableBumpTheAuthzVersion() {
		User user = newUser();

		user.disable("Fraud");
		user.enable();
		users.flush();

		assertThat(storedVersion(user)).isEqualTo(3);
		assertThat(jdbc.queryForObject("select disabled_reason from users where user_id = ?", String.class,
				user.getUserId())).isNull();
	}

	private long storedVersion(User user) {
		users.flush();
		return jdbc.queryForObject("select authz_version from users where user_id = ?", Long.class, user.getUserId());
	}

	private User newUser() {
		return users.save(User.local(UUID.randomUUID() + "@example.test", "{noop}x", "Test User", null));
	}

}
