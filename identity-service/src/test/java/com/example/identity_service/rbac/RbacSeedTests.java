package com.example.identity_service.rbac;

import com.example.identity_service.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "eureka.client.enabled=false")
@Import(TestcontainersConfiguration.class)
class RbacSeedTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void rolesAreExactlyCustomerSellerAndAdmin() {
		assertThat(jdbc.queryForList("select code from roles", String.class))
				.containsExactlyInAnyOrder("CUSTOMER", "SELLER", "ADMIN");
	}

	@Test
	void rolePermissionsMatchTheApiMatrix() {
		Map<String, Set<String>> seeded = new HashMap<>();
		jdbc.query("""
				select r.code as role_code, p.code as permission_code
				from role_permissions rp join roles r using (role_id) join permissions p using (permission_id)""",
				rs -> {
					seeded.computeIfAbsent(rs.getString("role_code"), k -> new HashSet<>())
							.add(rs.getString("permission_code"));
				});
		assertThat(seeded).isEqualTo(RbacMatrix.BY_ROLE);
	}

	@Test
	void baselinePermissionsAreCataloguedButBelongToNoRole() {
		List<String> catalogue = jdbc.queryForList("select code from permissions", String.class);
		assertThat(catalogue).containsExactlyInAnyOrderElementsOf(RbacMatrix.union(RbacMatrix.BASELINE,
				RbacMatrix.CUSTOMER, RbacMatrix.SELLER, RbacMatrix.ADMIN));

		List<String> linked = jdbc.queryForList(
				"select p.code from role_permissions rp join permissions p using (permission_id)", String.class);
		assertThat(linked).doesNotContainAnyElementsOf(RbacMatrix.BASELINE);
		assertThat(Permissions.BASELINE).isEqualTo(RbacMatrix.BASELINE);
	}

}
