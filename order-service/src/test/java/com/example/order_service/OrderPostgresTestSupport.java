package com.example.order_service;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.DriverManager;
import java.sql.SQLException;

abstract class OrderPostgresTestSupport {

	@DynamicPropertySource
	static void postgres(DynamicPropertyRegistry registry) throws SQLException {
		String url = required("ORDER_TEST_DATABASE_URL");
		String username = required("ORDER_TEST_DATABASE_USER");
		String password = required("ORDER_TEST_DATABASE_PASSWORD");
		try (var connection = DriverManager.getConnection(url, username, password);
				var statement = connection.createStatement();
				var result = statement.executeQuery("select current_setting('server_version_num')::integer, current_database()")) {
			if (!result.next() || result.getInt(1) / 10000 != 16 || !result.getString(2).startsWith("order_test_")) {
				throw new IllegalStateException("Use a dedicated order_test_ database on PostgreSQL 16");
			}
		}
		registry.add("spring.datasource.url", () -> url);
		registry.add("spring.datasource.username", () -> username);
		registry.add("spring.datasource.password", () -> password);
	}

	private static String required(String key) {
		String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Missing test database setting: " + key);
		}
		return value;
	}
}
