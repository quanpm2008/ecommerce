package com.example.gateway_service.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;

import com.example.gateway_service.support.GatewayClient;
import com.example.gateway_service.support.StubService;
import com.example.gateway_service.support.StubService.Reply;
import com.example.gateway_service.support.TestTokens;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Checklist §5 (body size, envelope), §6 (CORS) and the cookie pass-through of §8. The body limit is lowered to 1 KB
 * here; the default (1 MB) is checked in {@code GatewayServiceApplicationTests}. order-service has no instance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "eureka.client.enabled=false", "gateway.limits.max-body-size=1KB",
				"gateway.cors.allowed-origins=http://localhost:5173, https://shop.example" })
class EdgeTests {

	static final StubService IDENTITY = StubService.start("identity-service");

	static final StubService JWKS = TestTokens.startJwks(TestTokens.KEY);

	static final ObjectMapper JSON = new ObjectMapper();

	static final String SPA = "http://localhost:5173";

	@LocalServerPort
	int port;

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
		anonymous = new GatewayClient(port);
		IDENTITY.reset();
	}

	// §5 body size

	@Test
	void aDeclaredBodyOverTheLimitIsRefusedBeforeItIsRead() throws Exception {
		HttpResponse<String> response = anonymous.send("POST", "/api/v1/identity/auth/register", "x".repeat(1025),
				"Content-Type", "application/json", "X-Request-Id", "req-big");

		assertThat(response.statusCode()).isEqualTo(413);
		JsonNode error = JSON.readTree(response.body()).path("error");
		assertThat(error.path("code").asString()).isEqualTo("VALIDATION_ERROR");
		assertThat(error.path("message").asString()).isEqualTo("The request body must not be larger than 1 KB.");
		assertThat(error.path("requestId").asString()).isEqualTo("req-big");
		assertThat(IDENTITY.received()).isEmpty();
	}

	@Test
	void aBodyAtTheLimitIsForwarded() {
		HttpResponse<String> response = anonymous.send("POST", "/api/v1/identity/auth/register", "x".repeat(1024),
				"Content-Type", "application/json");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(IDENTITY.last().body()).hasSize(1024);
	}

	@Test
	void aChunkedBodyIsCountedWhileItStreams() throws Exception {
		HttpResponse<String> small = anonymous.sendChunked("POST", "/api/v1/identity/auth/register",
				"y".repeat(800).getBytes(), "Content-Type", "application/json");
		HttpResponse<String> big = anonymous.sendChunked("POST", "/api/v1/identity/auth/register",
				"z".repeat(64 * 1024).getBytes(), "Content-Type", "application/json");

		assertThat(small.statusCode()).isEqualTo(200);
		assertThat(IDENTITY.last().body()).hasSize(800);
		assertThat(big.statusCode()).isEqualTo(413);
		assertThat(JSON.readTree(big.body()).path("error").path("code").asString()).isEqualTo("VALIDATION_ERROR");
		assertThat(IDENTITY.received()).hasSize(1);
	}

	// §6 CORS

	@Test
	void aPreflightFromTheSpaIsAnsweredHereWithoutAToken() {
		HttpResponse<String> response = anonymous.send("OPTIONS", "/api/v1/identity/users/me", null, "Origin", SPA,
				"Access-Control-Request-Method", "PATCH",
				"Access-Control-Request-Headers", "authorization, content-type, idempotency-key, x-request-id");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).hasValue(SPA);
		assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).hasValue("true");
		assertThat(values(response, "Access-Control-Allow-Methods")).contains("GET", "POST", "PUT", "PATCH", "DELETE");
		assertThat(values(response, "Access-Control-Allow-Headers")).map(String::toLowerCase)
				.contains("authorization", "content-type", "idempotency-key", "x-request-id");
		assertThat(response.headers().firstValue("Access-Control-Max-Age")).hasValue("1800");
		assertThat(IDENTITY.received()).isEmpty();
	}

	@Test
	void eachConfiguredOriginIsAllowedAndOnlyThatOneIsEchoed() {
		HttpResponse<String> response = anonymous.get("/api/v1/identity/sellers", "Origin", "https://shop.example");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().allValues("Access-Control-Allow-Origin")).containsExactly("https://shop.example");
		assertThat(values(response, "Access-Control-Expose-Headers")).contains("X-Request-Id", "Retry-After");
		assertThat(IDENTITY.received()).hasSize(1);
	}

	@ParameterizedTest
	@CsvSource({ "GET, http://evil.example", "GET, http://localhost:5174", "OPTIONS, http://evil.example",
			"GET, null" })
	void anyOtherOriginIsRefused(String method, String origin) throws Exception {
		HttpResponse<String> response = method.equals("OPTIONS")
				? anonymous.send("OPTIONS", "/api/v1/identity/sellers", null, "Origin", origin,
						"Access-Control-Request-Method", "GET")
				: anonymous.get("/api/v1/identity/sellers", "Origin", origin);

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
		assertThat(JSON.readTree(response.body()).path("error").path("code").asString()).isEqualTo("FORBIDDEN");
		assertThat(IDENTITY.received()).isEmpty();
	}

	@Test
	void aPreflightForAMethodOrHeaderNotAllowedIsRefused() {
		HttpResponse<String> method = anonymous.send("OPTIONS", "/api/v1/identity/sellers", null, "Origin", SPA,
				"Access-Control-Request-Method", "TRACE");
		HttpResponse<String> header = anonymous.send("OPTIONS", "/api/v1/identity/sellers", null, "Origin", SPA,
				"Access-Control-Request-Method", "GET", "Access-Control-Request-Headers", "x-user-id");

		assertThat(method.statusCode()).isEqualTo(403);
		assertThat(header.statusCode()).isEqualTo(403);
	}

	@ParameterizedTest
	@CsvSource({ "/api/v1/nothing-here, 404", "/api/v1/identity/users/me, 401", "/api/v1/orders/o1, 503" })
	void gatewayErrorsCarryTheCorsHeadersToo(String path, int status) {
		GatewayClient client = status == 503 ? anonymous.withToken(TestTokens.valid()) : anonymous;

		HttpResponse<String> response = client.get(path, "Origin", SPA);

		assertThat(response.statusCode()).isEqualTo(status);
		assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).hasValue(SPA);
		assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).hasValue("true");
	}

	@Test
	void aServicesOwnCorsHeadersAreDroppedAndItsVaryIsKept() {
		IDENTITY.reply(Reply.json(200, "{}").withHeader("Access-Control-Allow-Origin", "*")
				.withHeader("Access-Control-Allow-Credentials", "false").withHeader("Vary", "Accept-Encoding"));

		HttpResponse<String> response = anonymous.get("/api/v1/identity/sellers", "Origin", SPA);

		assertThat(response.headers().allValues("Access-Control-Allow-Origin")).containsExactly(SPA);
		assertThat(response.headers().allValues("Access-Control-Allow-Credentials")).containsExactly("true");
		assertThat(values(response, "Vary")).contains("Origin", "Accept-Encoding");
	}

	@Test
	void requestsWithoutAnOriginGetNoCorsHeaders() {
		HttpResponse<String> response = anonymous.get("/api/v1/identity/sellers");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
	}

	// §8 cookies pass through; the gateway sets none of its own

	@Test
	void identitysRefreshCookiePassesThroughBothWays() {
		IDENTITY.reply(Reply.json(200, "{\"data\":{}}").withHeader("Set-Cookie",
				"refresh_token=rt-2; Path=/api/v1/identity/auth; HttpOnly; Secure; SameSite=Strict"));

		HttpResponse<String> response = anonymous.send("POST", "/api/v1/identity/auth/refresh", "{}", "Origin", SPA,
				"Cookie", "refresh_token=rt-1", "Content-Type", "application/json");

		assertThat(IDENTITY.last().header("Cookie")).isEqualTo("refresh_token=rt-1");
		assertThat(response.headers().allValues("Set-Cookie"))
				.containsExactly("refresh_token=rt-2; Path=/api/v1/identity/auth; HttpOnly; Secure; SameSite=Strict");
	}

	private static List<String> values(HttpResponse<String> response, String header) {
		return response.headers().allValues(header).stream()
				.flatMap(value -> Arrays.stream(value.split(",")))
				.map(String::trim)
				.toList();
	}

}
