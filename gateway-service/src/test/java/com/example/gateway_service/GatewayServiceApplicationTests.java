package com.example.gateway_service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;

import javax.sql.DataSource;

import java.time.Duration;

import com.example.gateway_service.support.GatewayClient;
import com.example.gateway_service.web.EdgeProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.util.unit.DataSize;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Checklist §0: starts on the reactive stack without Eureka or a database, exposes only a detail-free health endpoint,
 * stays stateless.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "eureka.client.enabled=false")
class GatewayServiceApplicationTests {

	static final ObjectMapper JSON = new ObjectMapper();

	@LocalServerPort
	int port;

	@Autowired
	ApplicationContext context;

	GatewayClient gateway;

	@BeforeEach
	void setUp() {
		gateway = new GatewayClient(port);
	}

	@Test
	void startsWithoutADatabase() {
		assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
	}

	@Test
	void edgeDefaultsAreTheDecidedOnes() {
		EdgeProperties edge = context.getBean(EdgeProperties.class);

		assertThat(edge.cors().allowedOrigins()).containsExactly("http://localhost:5173");
		assertThat(edge.limits().maxBodySize()).isEqualTo(DataSize.ofMegabytes(1));
		assertThat(edge.rateLimit().sensitive()).isEqualTo(new EdgeProperties.Tier(10, Duration.ofMinutes(1)));
		assertThat(edge.rateLimit().general()).isEqualTo(new EdgeProperties.Tier(300, Duration.ofMinutes(1)));
	}

	@Test
	void runsOnTheReactiveStackOnly() {
		assertThat(context.getClassLoader()).satisfies(loader -> {
			assertThat(isPresent("org.springframework.web.servlet.DispatcherServlet", loader)).isFalse();
			assertThat(isPresent("org.apache.catalina.startup.Tomcat", loader)).isFalse();
			assertThat(isPresent("org.springframework.web.reactive.DispatcherHandler", loader)).isTrue();
		});
	}

	@Test
	void healthIsPublicAndHasNoDetails() throws Exception {
		HttpResponse<String> response = gateway.get("/actuator/health");

		assertThat(response.statusCode()).isEqualTo(200);
		JsonNode health = JSON.readTree(response.body());
		assertThat(health.path("status").asString()).isEqualTo("UP");
		assertThat(health.has("components")).isFalse();
		assertThat(health.has("details")).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "/actuator", "/actuator/env", "/actuator/beans", "/actuator/info", "/actuator/mappings",
			"/actuator/gateway/routes", "/actuator/health/discoveryComposite" })
	void otherActuatorEndpointsAreNotExposed(String path) {
		assertThat(gateway.get(path).statusCode()).isEqualTo(404);
	}

	@Test
	void temporaryHelloEndpointIsGone() {
		assertThat(gateway.get("/hello").statusCode()).isEqualTo(404);
	}

	@Test
	void isStatelessAndOffersNoLoginChallenge() {
		HttpResponse<String> health = gateway.get("/actuator/health");

		assertThat(health.headers().firstValue("Set-Cookie")).isEmpty();
		assertThat(health.headers().firstValue("WWW-Authenticate")).isEmpty();
		assertThat(gateway.get("/login").statusCode()).isEqualTo(404);
		assertThat(gateway.get("/logout").statusCode()).isEqualTo(404);
	}

	private static boolean isPresent(String className, ClassLoader loader) {
		try {
			Class.forName(className, false, loader);
			return true;
		}
		catch (ClassNotFoundException ex) {
			return false;
		}
	}

}
