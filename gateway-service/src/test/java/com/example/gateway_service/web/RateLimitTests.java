package com.example.gateway_service.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Duration;

import com.example.gateway_service.support.GatewayClient;
import com.example.gateway_service.support.StubService;
import com.example.gateway_service.support.TestTokens;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Checklist §7 with small limits: sensitive endpoints 3 per 3 s (one more each second), everything else 5 per minute.
 * Every request comes from 127.0.0.1; counts are cleared before each test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "eureka.client.enabled=false", "gateway.rate-limit.sensitive.requests=3",
				"gateway.rate-limit.sensitive.window=3s", "gateway.rate-limit.general.requests=5",
				"gateway.rate-limit.general.window=1m" })
class RateLimitTests {

	static final StubService IDENTITY = StubService.start("identity-service");

	static final StubService JWKS = TestTokens.startJwks(TestTokens.KEY);

	static final ObjectMapper JSON = new ObjectMapper();

	@LocalServerPort
	int port;

	@Autowired
	RateLimitFilter limits;

	GatewayClient anonymous;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.cloud.discovery.client.simple.instances.identity-service[0].uri", IDENTITY::uri);
		registry.add("gateway.jwt.jwk-set-uri", () -> TestTokens.jwksUri(JWKS));
	}

	@AfterAll
	static void stopStubs() {
		IDENTITY.close();
		JWKS.close();
	}

	@BeforeEach
	void setUp() {
		limits.clear();
		anonymous = new GatewayClient(port);
		IDENTITY.reset();
	}

	@Test
	void sensitiveEndpointsGetATooManyRequestsEnvelopeWithRetryAfter() throws Exception {
		for (int i = 0; i < 3; i++) {
			assertThat(login().statusCode()).isEqualTo(200);
		}

		HttpResponse<String> limited = login("X-Request-Id", "req-429", "Origin", "http://localhost:5173");

		assertThat(limited.statusCode()).isEqualTo(429);
		assertThat(limited.headers().firstValue("Retry-After")).hasValue("1");
		assertThat(limited.headers().firstValue("X-Request-Id")).hasValue("req-429");
		assertThat(limited.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://localhost:5173");
		JsonNode error = JSON.readTree(limited.body()).path("error");
		assertThat(error.path("code").asString()).isEqualTo("RATE_LIMITED");
		assertThat(error.path("requestId").asString()).isEqualTo("req-429");
		assertThat(IDENTITY.received()).hasSize(3);
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/v1/identity/auth/register", "/api/v1/identity/auth/register-seller",
			"/api/v1/identity/auth/refresh", "/api/v1/identity/auth/password/forgot",
			"/api/v1/identity/auth/password/reset", "/api/v1/identity/auth/seller-reapplication/challenge",
			"/api/v1/identity/auth/seller-reapplication" })
	void everySensitiveEndpointSharesTheStricterLimit(String path) {
		for (int i = 0; i < 3; i++) {
			assertThat(anonymous.send("POST", path, "{}", "Content-Type", "application/json").statusCode())
					.isEqualTo(200);
		}

		assertThat(login().statusCode()).isEqualTo(429);
	}

	@Test
	void fakingTheForwardedForHeaderDoesNotResetTheCount() {
		for (int i = 0; i < 3; i++) {
			login("X-Forwarded-For", "198.51.100." + i);
		}

		assertThat(login("X-Forwarded-For", "203.0.113.77").statusCode()).isEqualTo(429);
	}

	@Test
	void theBucketRefillsOverTheWindow() throws Exception {
		for (int i = 0; i < 3; i++) {
			login();
		}
		assertThat(login().statusCode()).isEqualTo(429);

		Thread.sleep(Duration.ofMillis(1100));

		assertThat(login().statusCode()).isEqualTo(200);
		assertThat(login().statusCode()).isEqualTo(429);
	}

	@Test
	void theTwoTiersAreCountedApart() {
		for (int i = 0; i < 3; i++) {
			login();
		}
		assertThat(login().statusCode()).isEqualTo(429);

		assertThat(anonymous.get("/api/v1/identity/sellers").statusCode()).isEqualTo(200);
	}

	@Test
	void everyOtherRequestHasTheGeneralLimitButHealthAndPreflightsAreNotCounted() {
		for (int i = 0; i < 10; i++) {
			assertThat(anonymous.get("/actuator/health").statusCode()).isEqualTo(200);
			assertThat(anonymous.send("OPTIONS", "/api/v1/identity/sellers", null, "Origin", "http://localhost:5173",
					"Access-Control-Request-Method", "GET").statusCode()).isEqualTo(200);
		}
		for (int i = 0; i < 5; i++) {
			assertThat(anonymous.get(i % 2 == 0 ? "/api/v1/identity/sellers" : "/api/v1/nothing-here").statusCode())
					.isIn(200, 404);
		}

		HttpResponse<String> limited = anonymous.get("/api/v1/identity/sellers");

		assertThat(limited.statusCode()).isEqualTo(429);
		assertThat(Long.parseLong(limited.headers().firstValue("Retry-After").orElseThrow())).isBetween(1L, 12L);
	}

	@Test
	void aGetOnASensitivePathIsInTheGeneralTier() {
		for (int i = 0; i < 3; i++) {
			login();
		}

		assertThat(anonymous.get("/api/v1/identity/auth/login").statusCode()).isEqualTo(401);
	}

	private HttpResponse<String> login(String... headers) {
		String[] all = new String[headers.length + 2];
		all[0] = "Content-Type";
		all[1] = "application/json";
		System.arraycopy(headers, 0, all, 2, headers.length);
		return anonymous.send("POST", "/api/v1/identity/auth/login", "{}", all);
	}

}
