package com.example.gateway_service.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;

import com.example.gateway_service.support.GatewayClient;
import com.example.gateway_service.support.StubService;
import com.example.gateway_service.support.TestTokens;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.ObjectMapper;

/**
 * Checklist §4: which headers a service gets, and the request id. identity-service and product-service are stubs;
 * order-service has no instance (so its routes fail with 503).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "eureka.client.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class HeadersTests {

	static final StubService IDENTITY = StubService.start("identity-service");

	static final StubService JWKS = TestTokens.startJwks(TestTokens.KEY);

	static final ObjectMapper JSON = new ObjectMapper();

	static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	@LocalServerPort
	int port;

	GatewayClient anonymous;

	GatewayClient signedIn;

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
		signedIn = anonymous.withToken(TestTokens.valid());
		IDENTITY.reset();
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/v1/identity/sellers", "/api/v1/identity/users/me" })
	void identityHeadersSentByTheClientAreRemoved(String path) {
		HttpResponse<String> response = signedIn.get(path, "X-User-Id", "someone-else", "x-user-id", "again",
				"X-USER-ID", "third", "X-Actor-Id", "a1", "X-Actor-Type", "ADMIN", "x-permissions", "seller:approve");

		assertThat(response.statusCode()).isEqualTo(200);
		StubService.Received received = IDENTITY.last();
		assertThat(received.headers().keySet()).noneMatch(name -> List.of("x-user-id", "x-actor-id", "x-actor-type",
				"x-permissions").contains(name.toLowerCase()));
	}

	@Test
	void theGatewayAddsNoIdentityHeadersOfItsOwn() {
		String token = TestTokens.valid();

		anonymous.withToken(token).get("/api/v1/identity/users/me");

		StubService.Received received = IDENTITY.last();
		assertThat(received.headers().keySet()).noneMatch(name -> name.toLowerCase().matches("x-(user|actor|permissions).*"));
		assertThat(received.header("Authorization")).isEqualTo("Bearer " + token);
	}

	@Test
	void requestHeadersTheServicesNeedPassThrough() {
		signedIn.send("PATCH", "/api/v1/identity/users/me", "{\"fullName\":\"A\"}", "Content-Type",
				"application/json", "Idempotency-Key", "idem-1", "Accept-Language", "vi-VN", "If-Match", "\"3\"",
				"Cookie", "refresh_token=rt-1");

		StubService.Received received = IDENTITY.last();
		assertThat(received.header("Content-Type")).startsWith("application/json");
		assertThat(received.header("Idempotency-Key")).isEqualTo("idem-1");
		assertThat(received.header("Accept-Language")).isEqualTo("vi-VN");
		assertThat(received.header("If-Match")).isEqualTo("\"3\"");
		assertThat(received.header("Cookie")).isEqualTo("refresh_token=rt-1");
	}

	@Test
	void forwardedHeadersDescribeTheRealConnectionNotWhatTheClientClaims() {
		anonymous.get("/api/v1/identity/sellers", "X-Forwarded-For", "203.0.113.9", "X-Forwarded-Host", "evil.example",
				"X-Forwarded-Proto", "https", "X-Forwarded-Port", "443", "Forwarded", "for=203.0.113.9",
				"X-Forwarded-Prefix", "/evil");

		StubService.Received received = IDENTITY.last();
		assertThat(received.header("X-Forwarded-For")).isEqualTo("127.0.0.1");
		assertThat(received.header("X-Forwarded-Proto")).isEqualTo("http");
		assertThat(received.header("X-Forwarded-Host")).isEqualTo("127.0.0.1:" + port);
		assertThat(received.header("X-Forwarded-Port")).isEqualTo(Integer.toString(port));
		assertThat(received.header("Forwarded")).isNull();
		assertThat(received.header("X-Forwarded-Prefix")).isNull();
	}

	@Test
	void hopByHopHeadersStopAtTheGatewayAndHostIsTheServices() {
		anonymous.get("/api/v1/identity/sellers", "Proxy-Authorization", "Basic eDp5", "Keep-Alive", "timeout=5");

		StubService.Received received = IDENTITY.last();
		assertThat(received.header("Proxy-Authorization")).isNull();
		assertThat(received.header("Keep-Alive")).isNull();
		assertThat(received.header("Host")).isEqualTo(IDENTITY.uri().substring("http://".length()));
	}

	@Test
	void aMissingRequestIdIsGeneratedForwardedAndReturned() {
		HttpResponse<String> response = anonymous.get("/api/v1/identity/sellers");

		String id = response.headers().firstValue("X-Request-Id").orElseThrow();
		assertThat(id).matches(UUID_PATTERN);
		assertThat(IDENTITY.last().header("X-Request-Id")).isEqualTo(id);
	}

	@Test
	void aSafeRequestIdIsKept() {
		HttpResponse<String> response = anonymous.get("/api/v1/identity/sellers", "X-Request-Id", "fe-Req_1.2");

		assertThat(response.headers().firstValue("X-Request-Id")).hasValue("fe-Req_1.2");
		assertThat(IDENTITY.last().header("X-Request-Id")).isEqualTo("fe-Req_1.2");
	}

	@ParameterizedTest
	@ValueSource(strings = { "has space", "semi;colon", "ünïcode", "<script>" })
	void anUnsafeRequestIdIsReplaced(String unsafe) {
		HttpResponse<String> response = anonymous.get("/api/v1/identity/sellers", "X-Request-Id", unsafe);

		String id = response.headers().firstValue("X-Request-Id").orElseThrow();
		assertThat(id).matches(UUID_PATTERN);
		assertThat(IDENTITY.last().header("X-Request-Id")).isEqualTo(id);
	}

	@Test
	void anOverlongOrRepeatedRequestIdIsReplaced() {
		HttpResponse<String> tooLong = anonymous.get("/api/v1/identity/sellers", "X-Request-Id", "a".repeat(129));
		HttpResponse<String> twice = anonymous.get("/api/v1/identity/sellers", "X-Request-Id", "one",
				"X-Request-Id", "two");

		assertThat(tooLong.headers().firstValue("X-Request-Id").orElseThrow()).matches(UUID_PATTERN);
		assertThat(twice.headers().allValues("X-Request-Id")).singleElement().asString().matches(UUID_PATTERN);
		assertThat(IDENTITY.last().headers().entrySet().stream()
				.filter(entry -> entry.getKey().equalsIgnoreCase("X-Request-Id"))
				.flatMap(entry -> entry.getValue().stream())).singleElement().asString().matches(UUID_PATTERN);
	}

	@ParameterizedTest
	@CsvSource({
			"/api/v1/nothing-here, 404",
			"/api/v1/identity/users/me, 401",
			"/internal/v1/identity/users/u1/contact, 404",
			"/api/v1/identity/..%2finternal, 400",
			"/api/v1/orders/o1, 503" })
	void gatewayErrorsCarryTheRequestIdInTheHeaderAndTheBody(String path, int status) throws Exception {
		GatewayClient client = status == 503 ? signedIn : anonymous;

		HttpResponse<String> generated = client.get(path);
		HttpResponse<String> sent = client.get(path, "X-Request-Id", "req-err");

		assertThat(generated.statusCode()).isEqualTo(status);
		String id = generated.headers().firstValue("X-Request-Id").orElseThrow();
		assertThat(id).matches(UUID_PATTERN);
		assertThat(JSON.readTree(generated.body()).path("error").path("requestId").asString()).isEqualTo(id);
		assertThat(sent.headers().firstValue("X-Request-Id")).hasValue("req-err");
		assertThat(JSON.readTree(sent.body()).path("error").path("requestId").asString()).isEqualTo("req-err");
	}

	@Test
	void aServicesOwnRequestIdHeaderDoesNotChangeTheOneReturned() {
		IDENTITY.reply(StubService.Reply.json(200, "{}").withHeader("X-Request-Id", "set-by-service"));

		HttpResponse<String> response = anonymous.get("/api/v1/identity/sellers", "X-Request-Id", "req-mine");

		assertThat(response.headers().allValues("X-Request-Id")).containsExactly("req-mine");
	}

	@Test
	void logLinesForTheRequestCarryItsId(CapturedOutput output) {
		signedIn.get("/api/v1/orders/o1", "X-Request-Id", "req-mdc-1");

		assertThat(output.getOut().lines().filter(line -> line.contains("requestId=req-mdc-1")))
				.anyMatch(line -> line.contains("No servers available for service: order-service"))
				.anyMatch(line -> line.contains("downstream unavailable"));
	}

}
