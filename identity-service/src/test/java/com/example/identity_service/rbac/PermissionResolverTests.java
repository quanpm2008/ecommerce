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
	RoleRepository roles;

	@Autowired
	UserRoleRepository userRoles;

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
		grant(user, RoleCode.CUSTOMER).revoke();

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

		assertThat(user.getAuthzVersion()).isEqualTo(2);
		assertThat(resolver.permissionsOf(user)).isEmpty();
	}

	private User newUser() {
		return users.save(User.local(UUID.randomUUID() + "@example.test", "{noop}x", "Test User", null));
	}

	private UserRole grant(User user, RoleCode code) {
		return userRoles.save(new UserRole(user.getUserId(), roles.findByCode(code).orElseThrow(), null));
	}

}
