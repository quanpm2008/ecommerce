package com.example.gateway_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.gateway_service.routing.ServiceRoute;
import com.example.gateway_service.support.GatewayClient;
import com.example.gateway_service.support.StubService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Checklist §2: nothing under {@code /internal} is reachable from outside, however the path is spelled, and a
 * client-sent {@code X-Service-Token} never reaches a service. Every service is a stub that must see no request.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "eureka.client.enabled=false")
class InternalPathTests {

	static final Map<String, StubService> SERVICES = Arrays.stream(ServiceRoute.values())
			.map(ServiceRoute::serviceId)
			.collect(Collectors.toMap(Function.identity(), StubService::start));

	static final ObjectMapper JSON = new ObjectMapper();

	static final String SERVICE_TOKEN = "s".repeat(40);

	@LocalServerPort
	int port;

	GatewayClient gateway;

	@DynamicPropertySource
	static void instances(DynamicPropertyRegistry registry) {
		SERVICES.forEach((id, stub) -> registry.add(
				"spring.cloud.discovery.client.simple.instances." + id + "[0].uri", stub::uri));
	}

	@AfterAll
	static void stopStubs() {
		SERVICES.values().forEach(StubService::close);
	}

	@BeforeEach
	void setUp() {
		gateway = new GatewayClient(port);
		SERVICES.values().forEach(StubService::reset);
	}

	@ParameterizedTest
	@CsvSource({
			"POST, /internal/v1/identity/auth/authorization-context",
			"GET, /internal/v1/identity/users/u1/contact",
			"GET, /internal/v1/identity/sellers/status?ids=a,b",
			"POST, /internal/v1/notifications",
			"GET, /internal/v1/orders/checkout-sessions/s1/payable",
			"GET, /internal/v1/catalog/products/p1",
			"PUT, /internal/v1/inventory",
			"DELETE, /internal/v1/identity/x",
			"PATCH, /internal/v1/identity/x",
			"GET, /internal",
			"GET, /internal/",
			"GET, /INTERNAL/v1/identity/users/u1/contact",
			"GET, /Internal/v1/identity/users/u1/contact",
			"GET, /api/v1/internal/v1/identity/users/u1/contact",
			"GET, /api/v1/identity/internal/v1/identity/users/u1/contact",
			"GET, /api/v1/products/internal",
			"GET, /api/v1/identity/%69nternal/v1/identity/users/u1/contact",
			"GET, /%49NTERNAL/v1/identity/users/u1/contact" })
	void internalPathsAreNotFound(String method, String path) throws Exception {
		HttpResponse<String> response = gateway.send(method, path, "{}", "Content-Type", "application/json",
				"X-Service-Token", SERVICE_TOKEN, "Authorization", "Bearer not-checked-here", "X-Request-Id", "req-int");

		assertThat(response.statusCode()).isEqualTo(404);
		JsonNode error = JSON.readTree(response.body()).path("error");
		assertThat(error.path("code").asString()).isEqualTo("NOT_FOUND");
		assertThat(error.path("requestId").asString()).isEqualTo("req-int");
		assertNoServiceWasCalled();
	}

	/** Tricks that could hide or rebuild an {@code internal} segment: rejected (400) or normalized and blocked (404). */
	@ParameterizedTest
	@ValueSource(strings = { "/api/v1/identity/../../internal/v1/identity/users/u1/contact",
			"/api/v1/identity/%2e%2e/%2e%2e/internal/v1/identity/users/u1/contact",
			"/api/v1/identity/..%2f..%2finternal/v1/identity/users/u1/contact",
			"/api/v1/identity%2f..%2f..%2finternal/v1/identity/users/u1/contact",
			"/api/v1/identity/%2e%2e%2f%2e%2e%2finternal/v1/identity",
			"/api/v1/identity/..%5c..%5cinternal/v1/identity",
			"//internal/v1/identity/users/u1/contact",
			"/api/v1/identity//../internal/v1/identity",
			"/internal;x=1/v1/identity/users/u1/contact",
			"/internal%3bx=1/v1/identity/users/u1/contact",
			"/api/v1/identity/;/../../internal/v1/identity",
			"/internal/v1/identity/users/u1/contact/",
			"/./internal/v1/identity/users/u1/contact",
			"/api/v1/identity/./../../internal/v1/identity",
			"/api/v1/identity/%252e%252e/internal/v1/identity",
			"/api/v1/identity/inter%6eal/v1/identity" })
	void encodingTricksCannotReachInternalPaths(String path) throws Exception {
		HttpResponse<String> response = gateway.get(path, "X-Service-Token", SERVICE_TOKEN, "X-Request-Id", "req-trick");

		assertThat(response.statusCode()).isIn(400, 404);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/json"));
		JsonNode error = JSON.readTree(response.body()).path("error");
		assertThat(error.path("code").asString()).isEqualTo(response.statusCode() == 400 ? "VALIDATION_ERROR" : "NOT_FOUND");
		assertThat(error.path("requestId").asString()).isEqualTo("req-trick");
		assertNoServiceWasCalled();
	}

	@Test
	void serviceTokenIsRemovedOnPublicRoutes() {
		StubService identity = SERVICES.get("identity-service");

		HttpResponse<String> response = gateway.get("/api/v1/identity/users/me", "X-Service-Token", SERVICE_TOKEN,
				"x-service-token", "second-value", "X-SERVICE-TOKEN", "third-value");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(identity.received()).hasSize(1);
		assertThat(identity.last().header("X-Service-Token")).isNull();
	}

	@Test
	void publicPathsWithInternalAsPartOfAWordStillRoute() {
		HttpResponse<String> response = gateway.get("/api/v1/products?q=internalize&tag=internal");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(SERVICES.get("product-service").last().target()).isEqualTo("/api/v1/products?q=internalize&tag=internal");
	}

	private static void assertNoServiceWasCalled() {
		assertThat(SERVICES.values().stream().flatMap(stub -> stub.received().stream())).isEmpty();
	}

}
