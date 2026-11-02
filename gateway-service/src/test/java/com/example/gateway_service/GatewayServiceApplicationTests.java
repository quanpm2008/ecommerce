package com.example.gateway_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

/** Checklist §0: starts without Eureka or a database, exposes only a detail-free health endpoint, stays stateless. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class GatewayServiceApplicationTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	ApplicationContext context;

	@Test
	void startsWithoutADatabase() {
		assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
	}

	@Test
	void healthIsPublicAndHasNoDetails() throws Exception {
		mvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist())
				.andExpect(jsonPath("$.details").doesNotExist());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/actuator", "/actuator/env", "/actuator/beans", "/actuator/info", "/actuator/mappings",
			"/actuator/gateway/routes", "/actuator/health/discoveryComposite" })
	void otherActuatorEndpointsAreNotExposed(String path) throws Exception {
		mvc.perform(get(path)).andExpect(status().isNotFound());
	}

	@Test
	void temporaryHelloEndpointIsGone() throws Exception {
		mvc.perform(get("/hello")).andExpect(status().isNotFound());
	}

	@Test
	void isStatelessAndOffersNoLoginChallenge() throws Exception {
		mvc.perform(get("/actuator/health"))
				.andExpect(header().doesNotExist("Set-Cookie"))
				.andExpect(header().doesNotExist("WWW-Authenticate"))
				.andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
		mvc.perform(get("/login")).andExpect(status().isNotFound());
	}

}
