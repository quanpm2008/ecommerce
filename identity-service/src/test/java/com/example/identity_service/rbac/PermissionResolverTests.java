package com.example.identity_service.rbac;

import com.example.identity_service.TestcontainersConfiguration;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import org.junit.jupiter.api.Test;
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
	void activeUserWithoutRolesGetsOnlyTheBaselinePolicy() {
		User user = newUser();

		assertThat(resolver.activeRolesOf(user)).isEmpty();
		assertThat(resolver.permissionsOf(user)).containsExactlyInAnyOrderElementsOf(RbacMatrix.BASELINE);
	}

	@Test
	void permissionsAreTheUnionOfAllActiveRoles() {
		User user = newUser();
		grant(user, RoleCode.CUSTOMER);
		grant(user, RoleCode.SELLER);

		assertThat(resolver.activeRolesOf(user)).containsExactlyInAnyOrder(RoleCode.CUSTOMER, RoleCode.SELLER);
		assertThat(resolver.permissionsOf(user)).containsExactlyInAnyOrderElementsOf(
				RbacMatrix.union(RbacMatrix.BASELINE, RbacMatrix.CUSTOMER, RbacMatrix.SELLER));
	}

	@Test
	void revokedRoleGrantsNothing() {
		User user = newUser();
		grant(user, RoleCode.CUSTOMER);
		roleAssignments.revoke(user, RoleCode.CUSTOMER);

		assertThat(resolver.activeRolesOf(user)).isEmpty();
		assertThat(resolver.permissionsOf(user)).containsExactlyInAnyOrderElementsOf(RbacMatrix.BASELINE);
	}

	@Test
	void adminDoesNotGetBuyerOrSellerPermissions() {
		User user = newUser();
		grant(user, RoleCode.ADMIN);

		assertThat(resolver.permissionsOf(user))
				.containsExactlyInAnyOrderElementsOf(RbacMatrix.union(RbacMatrix.BASELINE, RbacMatrix.ADMIN))
				.doesNotContain("cart:manage:own", "product:manage:own");
	}

	@Test
	void disabledUserHasNoPermissions() {
		User user = newUser();
		grant(user, RoleCode.CUSTOMER);
		user.disable("Fraud");

		assertThat(user.getAuthzVersion()).as("one bump for the grant, one for disable").isEqualTo(3);
		assertThat(resolver.permissionsOf(user)).isEmpty();
	}

	private User newUser() {
		return users.save(User.local(UUID.randomUUID() + "@example.test", "{noop}x", "Test User", null));
	}

	private void grant(User user, RoleCode code) {
		roleAssignments.grant(user, code, null);
	}

}
