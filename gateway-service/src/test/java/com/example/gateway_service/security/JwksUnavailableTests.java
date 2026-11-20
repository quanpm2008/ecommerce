package com.example.gateway_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;

import com.example.gateway_service.support.GatewayClient;
import com.example.gateway_service.support.StubService;
import com.example.gateway_service.support.TestTokens;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Checklist §3: identity-service's JWKS can't be reached (not started yet, or down). The gateway still starts and serves
 * public routes; a token it can't check is a 503 {@code SERVICE_UNAVAILABLE}, never let through.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "eureka.client.enabled=false", "gateway.jwt.jwks-timeout=500ms" })
class JwksUnavailableTests {

	static final StubService IDENTITY = StubService.start("identity-service");

	static final ObjectMapper JSON = new ObjectMapper();

	@LocalServerPort
	int port;

	GatewayClient gateway;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.cloud.discovery.client.simple.instances.identity-service[0].uri", IDENTITY::uri);
		registry.add("gateway.jwt.jwk-set-uri", () -> StubService.refusedUri() + "/.well-known/jwks.json");
	}

	@AfterAll
	static void stopStubs() {
		IDENTITY.close();
	}

	@BeforeEach
	void setUp() {
		gateway = new GatewayClient(port);
		IDENTITY.reset();
	}

	@Test
	void publicRoutesWorkWithoutTheKeys() {
		assertThat(gateway.get("/api/v1/identity/regions/provinces").statusCode()).isEqualTo(200);
		assertThat(gateway.get("/actuator/health").statusCode()).isEqualTo(200);
	}

	@Test
	void aTokenThatCannotBeCheckedIsServiceUnavailable() throws Exception {
		HttpResponse<String> response = gateway.withToken(TestTokens.valid())
				.get("/api/v1/identity/users/me", "X-Request-Id", "req-keys");

		assertThat(response.statusCode()).isEqualTo(503);
		JsonNode error = JSON.readTree(response.body()).path("error");
		assertThat(error.path("code").asString()).isEqualTo("SERVICE_UNAVAILABLE");
		assertThat(error.path("requestId").asString()).isEqualTo("req-keys");
		assertThat(IDENTITY.received()).isEmpty();
	}

}
