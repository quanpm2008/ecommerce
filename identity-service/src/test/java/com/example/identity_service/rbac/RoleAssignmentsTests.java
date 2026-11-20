package com.example.identity_service.rbac;

import com.example.identity_service.TestcontainersConfiguration;
import com.example.identity_service.user.AccountType;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
		User user = newUser(AccountType.CUSTOMER);

		assertThat(roleAssignments.grant(user, RoleCode.CUSTOMER, null)).isTrue();
		assertThat(storedVersion(user)).isEqualTo(2);

		assertThat(roleAssignments.revoke(user, RoleCode.CUSTOMER)).isTrue();
		assertThat(storedVersion(user)).isEqualTo(3);
		assertThat(resolver.activeRolesOf(user)).isEmpty();
	}

	@Test
	void changesThatChangeNothingDoNotBump() {
		User user = newUser(AccountType.CUSTOMER);
		roleAssignments.grant(user, RoleCode.CUSTOMER, null);

		assertThat(roleAssignments.grant(user, RoleCode.CUSTOMER, null)).isFalse();
		assertThat(roleAssignments.revoke(user, RoleCode.SELLER)).isFalse();
		assertThat(storedVersion(user)).isEqualTo(2);
	}

	@Test
	void grantingARevokedRoleAgainReusesItsRow() {
		User user = newUser(AccountType.SELLER);
		User admin = newUser(AccountType.ADMIN);
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

	@ParameterizedTest
	@CsvSource({ "CUSTOMER, SELLER", "CUSTOMER, ADMIN", "SELLER, CUSTOMER", "SELLER, ADMIN", "ADMIN, CUSTOMER",
			"ADMIN, SELLER" })
	void anAccountCanOnlyHoldTheRoleOfItsType(AccountType type, RoleCode other) {
		User user = newUser(type);

		assertThatThrownBy(() -> roleAssignments.grant(user, other, null)).isInstanceOf(IllegalArgumentException.class);
		assertThat(resolver.activeRolesOf(user)).isEmpty();
		assertThat(storedVersion(user)).isEqualTo(1);
	}

	@Test
	void theDatabaseRefusesASecondOrMismatchedRoleToo() {
		User customer = newUser(AccountType.CUSTOMER);
		roleAssignments.grant(customer, RoleCode.CUSTOMER, null);
		users.flush();

		assertThatThrownBy(() -> jdbc.update("""
				insert into user_roles (user_id, role_id) select ?, role_id from roles where code = 'SELLER'""",
				customer.getUserId())).isInstanceOf(DataAccessException.class);
	}

	@Test
	void everyChangeWritesRolesChangedButOnlyAdminChangesAreAudited() {
		User user = newUser(AccountType.SELLER);
		User admin = newUser(AccountType.ADMIN);

		roleAssignments.grant(user, RoleCode.SELLER, admin.getUserId());
		roleAssignments.revoke(user, RoleCode.SELLER);
		roleAssignments.grant(user, RoleCode.SELLER, null);
		roleAssignments.revoke(user, RoleCode.SELLER, admin.getUserId());
		roleAssignments.revoke(user, RoleCode.SELLER, admin.getUserId());
		users.flush();

		assertThat(jdbc.queryForList("""
				select payload -> 'payload' ->> 'roleCodes' from outbox_events
				where event_type = 'user.roles_changed' and aggregate_id = ? order by version""", String.class,
				user.getUserId())).containsExactly("[\"SELLER\"]", "[]", "[\"SELLER\"]", "[]");
		assertThat(jdbc.queryForList("""
				select action || ' ' || (before_after -> 'before' ->> 'roles') || ' -> '
				       || (before_after -> 'after' ->> 'roles')
				from admin_audit_log where target_id = ? and actor_user_id = ? order by happened_at, action""", String.class,
				user.getUserId(), admin.getUserId()))
				.containsExactly("GRANT_ROLE [] -> [\"SELLER\"]", "REVOKE_ROLE [\"SELLER\"] -> []");
	}

	@Test
	void disableAndEnableBumpTheAuthzVersion() {
		User user = newUser(AccountType.CUSTOMER);

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

	private User newUser(AccountType type) {
		return users.save(User.local(type, UUID.randomUUID() + "@example.test", "{noop}x", "Test User", null));
	}

}
