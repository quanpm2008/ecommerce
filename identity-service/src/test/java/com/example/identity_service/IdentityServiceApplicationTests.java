package com.example.identity_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "eureka.client.enabled=false")
@Import(TestcontainersConfiguration.class)
class IdentityServiceApplicationTests {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void flywayAppliedEveryMigrationToTheIdentitySchema() {
		List<String> versions = jdbcTemplate.queryForList("""
				select version from identity.flyway_schema_history
				where success and version is not null order by installed_rank""", String.class);
		assertThat(versions).containsExactly("1", "2", "3", "4", "5");
	}

	@Test
	void identitySchemaHasExactlyTheSpecifiedTables() {
		List<String> tables = jdbcTemplate.queryForList(
				"select table_name from information_schema.tables where table_schema = 'identity'", String.class);
		// No LoginIdentifier or AdminAccount table: email/google_subject are unique on users and admins are User + ADMIN.
		assertThat(tables).containsExactlyInAnyOrder("flyway_schema_history", "users", "customers", "sellers",
				"customer_addresses", "roles", "permissions", "user_roles", "role_permissions", "auth_sessions",
				"rotated_refresh_tokens", "password_reset_tokens", "admin_audit_log", "outbox_events",
				"address_touches");
	}

}
