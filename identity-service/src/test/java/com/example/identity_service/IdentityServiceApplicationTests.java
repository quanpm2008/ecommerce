package com.example.identity_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

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
	void flywayManagesTheIdentitySchema() {
		Integer historyTables = jdbcTemplate.queryForObject(
				"select count(*) from information_schema.tables where table_schema = 'identity' and table_name = 'flyway_schema_history'",
				Integer.class);
		assertThat(historyTables).isEqualTo(1);
	}

}
