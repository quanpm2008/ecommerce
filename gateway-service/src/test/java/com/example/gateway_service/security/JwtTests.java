package com.example.gateway_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.example.gateway_service.routing.ServiceRoute;
import com.example.gateway_service.support.GatewayClient;
import com.example.gateway_service.support.StubService;
import com.example.gateway_service.support.TestTokens;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Checklist §3: which requests need a token, what a valid token is (RS256 from the JWKS, {@code iss}, {@code aud},
 * {@code exp} with 60 s skew, {@code sub}, {@code sessionId}), the 401 codes, and that a rejected request never reaches
 * a service. Services are stubs; the JWKS is a stub serving {@link TestTokens#KEY}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "eureka.client.enabled=false")
class JwtTests {

	static final Map<String, StubService> SERVICES = Arrays.stream(ServiceRoute.values())
			.map(ServiceRoute::serviceId)
			.collect(Collectors.toMap(Function.identity(), StubService::start));

	static final StubService JWKS = TestTokens.startJwks(TestTokens.KEY);

	static final RSAKey OTHER_KEY = TestTokens.newKey(TestTokens.KEY.getKeyID());

	static final ObjectMapper JSON = new ObjectMapper();

	@LocalServerPort
	int port;

	GatewayClient anonymous;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		SERVICES.forEach((id, stub) -> registry.add(
				"spring.cloud.discovery.client.simple.instances." + id + "[0].uri", stub::uri));
		registry.add("gateway.jwt.jwk-set-uri", () -> TestTokens.jwksUri(JWKS));
	}

	@AfterAll
	static void stopStubs() {
		SERVICES.values().forEach(StubService::close);
		JWKS.close();
	}

	@BeforeEach
	void setUp() {
		anonymous = new GatewayClient(port);
		SERVICES.values().forEach(StubService::reset);
		TestTokens.publish(JWKS, TestTokens.KEY);
	}

	@ParameterizedTest
	@CsvSource({
			"POST, /api/v1/identity/auth/register, identity-service",
			"POST, /api/v1/identity/auth/register-seller, identity-service",
			"POST, /api/v1/identity/auth/login, identity-service",
			"POST, /api/v1/identity/auth/google, identity-service",
			"POST, /api/v1/identity/auth/refresh, identity-service",
			"POST, /api/v1/identity/auth/password/forgot, identity-service",
			"POST, /api/v1/identity/auth/password/reset, identity-service",
			"GET, /api/v1/identity/regions/provinces, identity-service",
			"GET, /api/v1/identity/regions/provinces/01/wards, identity-service",
			"GET, /api/v1/identity/sellers?q=shop, identity-service",
			"GET, /api/v1/identity/sellers/s1, identity-service",
			"GET, /api/v1/categories, product-service",
			"GET, /api/v1/products?categoryId=c1, product-service",
			"GET, /api/v1/products/p1, product-service",
			"GET, /api/v1/products/p1/related, product-service",
			"GET, /api/v1/products/p1/reviews, product-service" })
	void publicEndpointsNeedNoToken(String method, String target, String service) {
		HttpResponse<String> response = anonymous.send(method, target, method.equals("POST") ? "{}" : null,
				"Content-Type", "application/json");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(SERVICES.get(service).last().target()).isEqualTo(target);
		assertThat(SERVICES.get(service).last().header("Authorization")).isNull();
	}

	@ParameterizedTest
	@CsvSource({
			"GET, /api/v1/identity/users/me",
			"PATCH, /api/v1/identity/users/me",
			"PUT, /api/v1/identity/users/me/password",
			"POST, /api/v1/identity/auth/logout",
			"GET, /api/v1/identity/admin/customers",
			"GET, /api/v1/identity/customers/me/addresses",
			"POST, /api/v1/identity/media/upload-urls",
			"GET, /api/v1/identity/sellers/s1/extra",
			"GET, /api/v1/identity/auth/login",
			"POST, /api/v1/products",
			"PATCH, /api/v1/products/p1",
			"POST, /api/v1/products/p1/reviews",
			"GET, /api/v1/products/p1/reviews/r1",
			"GET, /api/v1/seller/products",
			"POST, /api/v1/categories",
			"GET, /api/v1/admin/categories",
			"POST, /api/v1/media/upload-urls",
			"GET, /api/v1/cart",
			"POST, /api/v1/orders",
			"GET, /api/v1/payments/p1",
			"GET, /api/v1/finance/wallet",
			"GET, /api/v1/notifications" })
	void protectedRoutesNeedAToken(String method, String target) throws Exception {
		HttpResponse<String> response = anonymous.send(method, target, method.equals("GET") ? null : "{}",
				"Content-Type", "application/json", "X-Request-Id", "req-401");

		assertUnauthorized(response, "UNAUTHENTICATED", "req-401");
		assertThat(response.headers().firstValue("WWW-Authenticate")).hasValue("Bearer");
		assertNoServiceWasCalled();
	}

	@Test
	void validTokenIsForwardedUnchanged() {
		String token = TestTokens.valid();

		HttpResponse<String> response = anonymous.withToken(token).get("/api/v1/identity/users/me");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(SERVICES.get("identity-service").last().header("Authorization")).isEqualTo("Bearer " + token);
	}

	@Test
	void validTokenOnAPublicEndpointIsForwardedToo() {
		String token = TestTokens.valid();

		anonymous.withToken(token).get("/api/v1/products");

		assertThat(SERVICES.get("product-service").last().header("Authorization")).isEqualTo("Bearer " + token);
	}

	@ParameterizedTest
	@ValueSource(strings = { "ecommerce-api", "other-api,ecommerce-api" })
	void audienceMustContainTheApi(String audiences) {
		String token = TestTokens.sign(TestTokens.KEY,
				TestTokens.claims().audience(List.of(audiences.split(","))).build());

		assertThat(anonymous.withToken(token).get("/api/v1/cart").statusCode()).isEqualTo(200);
	}

	static Stream<Arguments> invalidTokens() {
		Instant now = Instant.now();
		return Stream.of(
				Arguments.of("alg none", TestTokens.unsigned(TestTokens.claims().build())),
				Arguments.of("HS256 with the public key", TestTokens.hs256WithPublicKey(TestTokens.KEY,
						TestTokens.claims().build())),
				Arguments.of("signed by another key with the same kid", TestTokens.sign(OTHER_KEY,
						TestTokens.claims().build())),
				Arguments.of("RS512, not RS256", TestTokens.sign(TestTokens.KEY, JWSAlgorithm.RS512,
						TestTokens.claims().build())),
				Arguments.of("PS256, not RS256", TestTokens.sign(TestTokens.KEY, JWSAlgorithm.PS256,
						TestTokens.claims().build())),
				Arguments.of("wrong issuer", valid(TestTokens.claims().issuer("someone-else"))),
				Arguments.of("no issuer", valid(TestTokens.claims().issuer(null))),
				Arguments.of("wrong audience", valid(TestTokens.claims().audience("other-api"))),
				Arguments.of("no audience", valid(TestTokens.claims().audience((String) null))),
				Arguments.of("no sub", valid(TestTokens.claims().subject(null))),
				Arguments.of("blank sub", valid(TestTokens.claims().subject(" "))),
				Arguments.of("no sessionId", valid(TestTokens.claims().claim("sessionId", null))),
				Arguments.of("blank sessionId", valid(TestTokens.claims().claim("sessionId", ""))),
				Arguments.of("numeric sessionId", valid(TestTokens.claims().claim("sessionId", 42))),
				Arguments.of("no exp", valid(TestTokens.claims().expirationTime(null))),
				Arguments.of("nbf in the future", valid(TestTokens.claims()
						.notBeforeTime(Date.from(now.plus(Duration.ofMinutes(5)))))),
				Arguments.of("expired and wrong audience", valid(TestTokens.claims().audience("other-api")
						.expirationTime(Date.from(now.minus(Duration.ofMinutes(5)))))),
				Arguments.of("not a JWT", "abc.def.ghi"),
				Arguments.of("tampered payload", tamper(TestTokens.valid())));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidTokens")
	void invalidTokensAreRejectedOnProtectedAndPublicRoutes(String name, String token) throws Exception {
		for (String target : List.of("/api/v1/identity/users/me", "/api/v1/products")) {
			HttpResponse<String> response = anonymous.withToken(token).get(target, "X-Request-Id", "req-bad");

			assertUnauthorized(response, "UNAUTHENTICATED", "req-bad");
			assertThat(response.headers().firstValue("WWW-Authenticate")).hasValue("Bearer error=\"invalid_token\"");
		}
		assertNoServiceWasCalled();
	}

	@Test
	void expiredTokenIsTokenExpired() throws Exception {
		String token = valid(TestTokens.claims()
				.issueTime(Date.from(Instant.now().minus(Duration.ofMinutes(20))))
				.expirationTime(Date.from(Instant.now().minus(Duration.ofMinutes(2)))));

		for (String target : List.of("/api/v1/identity/users/me", "/api/v1/products")) {
			assertUnauthorized(anonymous.withToken(token).get(target), "TOKEN_EXPIRED", null);
		}
		assertNoServiceWasCalled();
	}

	@Test
	void tokenExpiredWithinTheClockSkewIsAccepted() {
		String token = valid(TestTokens.claims()
				.issueTime(Date.from(Instant.now().minus(Duration.ofMinutes(15))))
				.expirationTime(Date.from(Instant.now().minus(Duration.ofSeconds(20)))));

		assertThat(anonymous.withToken(token).get("/api/v1/identity/users/me").statusCode()).isEqualTo(200);
	}

	@ParameterizedTest
	@ValueSource(strings = { "Bearer", "Bearer ", "Bearer  ", "Basic dXNlcjpwYXNzd29yZA==", "Token abc",
			"Bearer a b", "Bearer abc def.ghi", "Bearertoken" })
	void malformedAuthorizationHeadersAreRejected(String header) throws Exception {
		for (String target : List.of("/api/v1/identity/users/me", "/api/v1/products")) {
			HttpResponse<String> response = anonymous.get(target, "Authorization", header);

			assertUnauthorized(response, "UNAUTHENTICATED", null);
		}
		assertNoServiceWasCalled();
	}

	@Test
	void twoAuthorizationHeadersAreRejected() throws Exception {
		String token = TestTokens.valid();

		HttpResponse<String> response = anonymous.get("/api/v1/products", "Authorization", "Bearer " + token,
				"Authorization", "Bearer " + token);

		assertUnauthorized(response, "UNAUTHENTICATED", null);
		assertNoServiceWasCalled();
	}

	@Test
	void schemeIsCaseInsensitive() {
		HttpResponse<String> response = anonymous.get("/api/v1/cart", "Authorization", "bearer " + TestTokens.valid());

		assertThat(response.statusCode()).isEqualTo(200);
	}

	@Test
	void tokensInTheQueryStringAreNotAccepted() throws Exception {
		HttpResponse<String> response = anonymous.get("/api/v1/identity/users/me?access_token=" + TestTokens.valid());

		assertUnauthorized(response, "UNAUTHENTICATED", null);
		assertNoServiceWasCalled();
	}

	@Test
	void aNewSigningKeyIsPickedUpWithoutRestart() {
		assertThat(anonymous.withToken(TestTokens.valid()).get("/api/v1/cart").statusCode()).isEqualTo(200);
		RSAKey rotated = TestTokens.newKey("test-key-b");
		TestTokens.publish(JWKS, rotated);

		String token = TestTokens.sign(rotated, TestTokens.claims().build());

		assertThat(anonymous.withToken(token).get("/api/v1/cart").statusCode()).isEqualTo(200);
	}

	@Test
	void unknownPathsStay404WithoutAToken() {
		assertThat(anonymous.get("/api/v1/nothing-here").statusCode()).isEqualTo(404);
		assertThat(anonymous.get("/actuator/health").statusCode()).isEqualTo(200);
	}

	private static String valid(JWTClaimsSet.Builder claims) {
		return TestTokens.sign(TestTokens.KEY, claims.build());
	}

	/** Changes the payload but keeps the original signature. */
	private static String tamper(String token) {
		String[] parts = token.split("\\.");
		String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
				TestTokens.claims().subject("someone-else").build().toString().getBytes());
		return parts[0] + "." + payload + "." + parts[2];
	}

	private static void assertUnauthorized(HttpResponse<String> response, String code, String requestId)
			throws Exception {
		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/json"));
		JsonNode error = JSON.readTree(response.body()).path("error");
		assertThat(error.path("code").asString()).isEqualTo(code);
		assertThat(error.path("message").asString()).isNotBlank();
		assertThat(error.path("details").isArray()).isTrue();
		if (requestId != null) {
			assertThat(error.path("requestId").asString()).isEqualTo(requestId);
		}
		assertThat(response.body()).doesNotContain("Exception", "eyJ");
	}

	private static void assertNoServiceWasCalled() {
		assertThat(SERVICES.values().stream().flatMap(stub -> stub.received().stream())).isEmpty();
	}

}
