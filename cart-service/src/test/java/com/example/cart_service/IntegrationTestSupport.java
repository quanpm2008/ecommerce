package com.example.cart_service;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full application on a throwaway PostgreSQL (Flyway migrations, real transactions and locks) with Identity, Product
 * and Order replaced by {@link FakeRemotes}. Kafka stays off; event handling is called directly.
 */
@SpringBootTest(properties = { "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, FakeRemotes.Configuration.class })
public abstract class IntegrationTestSupport {

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected FakeRemotes remotes;

	@Autowired
	protected JdbcTemplate jdbc;

	@BeforeEach
	void cleanState() {
		remotes.reset();
		jdbc.update("DELETE FROM inbox_events");
		jdbc.update("DELETE FROM cart_items");
		jdbc.update("DELETE FROM carts");
	}

}
