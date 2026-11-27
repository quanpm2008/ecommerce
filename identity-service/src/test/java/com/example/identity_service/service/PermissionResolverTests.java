package com.example.identity_service.service;

import com.example.identity_service.entity.RoleCode;
import com.example.identity_service.TestcontainersConfiguration;
import com.example.identity_service.entity.AccountType;
import com.example.identity_service.entity.User;
import com.example.identity_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "eureka.client.enabled=false")
@Import(TestcontainersConfiguration.class)
@Transactional
class PermissionResolverTests {

	@Autowired
	PermissionResolver resolver;

	@Autowired
	UserRepository users;

	@Autowired
	RoleAssignments roleAssignments;

	@Test
	void activeUserWithoutARoleGetsOnlyTheBaselinePolicy() {
		User user = newUser(AccountType.SELLER);

		assertThat(resolver.activeRolesOf(user)).isEmpty();
		assertThat(resolver.permissionsOf(user)).containsExactlyInAnyOrderElementsOf(RbacMatrix.BASELINE);
	}

	@ParameterizedTest
	@EnumSource(AccountType.class)
	void permissionsAreTheBaselinePlusTheAccountsOneRole(AccountType type) {
		User user = newUser(type);
		grant(user, type.role());

		assertThat(resolver.activeRolesOf(user)).containsExactly(type.role());
		assertThat(resolver.permissionsOf(user)).containsExactlyInAnyOrderElementsOf(
				RbacMatrix.union(RbacMatrix.BASELINE, RbacMatrix.of(type.role())));
	}

	@Test
	void revokedRoleGrantsNothing() {
		User user = newUser(AccountType.CUSTOMER);
		grant(user, RoleCode.CUSTOMER);
		roleAssignments.revoke(user, RoleCode.CUSTOMER);

		assertThat(resolver.activeRolesOf(user)).isEmpty();
		assertThat(resolver.permissionsOf(user)).containsExactlyInAnyOrderElementsOf(RbacMatrix.BASELINE);
	}

	@Test
	void adminDoesNotGetBuyerOrSellerPermissions() {
		User user = newUser(AccountType.ADMIN);
		grant(user, RoleCode.ADMIN);

		assertThat(resolver.permissionsOf(user))
				.containsExactlyInAnyOrderElementsOf(RbacMatrix.union(RbacMatrix.BASELINE, RbacMatrix.ADMIN))
				.doesNotContain("cart:manage:own", "product:manage:own", "refund:retry:own");
	}

	@Test
	void disabledUserHasNoPermissions() {
		User user = newUser(AccountType.CUSTOMER);
		grant(user, RoleCode.CUSTOMER);
		user.startBan(java.time.Instant.now(), com.example.identity_service.entity.AccessMode.LOGIN_BLOCKED);
		user.disableForBan("Khác: Fraud", java.time.Instant.now());

		assertThat(user.getAuthzVersion()).as("one bump for the grant, two for the ban").isEqualTo(4);
		assertThat(resolver.permissionsOf(user)).isEmpty();
	}

	private User newUser(AccountType type) {
		return users.save(User.local(type, UUID.randomUUID() + "@example.test", "{noop}x", "Test User", null));
	}

	private void grant(User user, RoleCode code) {
		roleAssignments.grant(user, code, null);
	}

}
