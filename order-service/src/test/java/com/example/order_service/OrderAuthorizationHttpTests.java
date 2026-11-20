package com.example.order_service;

import com.example.order_service.security.OrderAuthorization.CurrentUser;
import com.example.order_service.security.OrderAuthorization;
import com.example.order_service.security.OrderSecurityConfig;
import com.example.order_service.web.OrderApiExceptionHandler;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OrderAuthorizationHttpTests.SecurityFixture.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"eureka.client.enabled=false", "logging.level.root=WARN",
		"order.internal.tokens.product=inventory-fixture-token", "order.identity.service-token=order-outbound-fixture"
})
class OrderAuthorizationHttpTests extends OrderPostgresTestSupport {
	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@Import({OrderSecurityConfig.class, OrderAuthorization.class, OrderApiExceptionHandler.class, OrderRouteFixture.class})
	static class SecurityFixture {
	}
	private static final String PREVIEW = "/api/v1/orders/checkout/preview";
	private static final String INVENTORY = "/api/v1/orders/seller/inventory";
	private static final UUID USER = UUID.randomUUID();
	private static final UUID SESSION = UUID.randomUUID();
	private static final RSAKey KEY = rsaKey();
	private static final AtomicReference<Reply> REPLY = new AtomicReference<>();
	private static final AtomicReference<Call> CALL = new AtomicReference<>();
	private static final AtomicInteger CALLS = new AtomicInteger();
	private static final HttpServer IDENTITY = startIdentity();

	@LocalServerPort
	private int port;
	@Autowired
	private JsonMapper json;

	@DynamicPropertySource
	static void identity(DynamicPropertyRegistry properties) {
		properties.add("order.identity.base-url", () -> "http://127.0.0.1:" + IDENTITY.getAddress().getPort());
	}

	@BeforeEach
	void resetIdentity() {
		CALLS.set(0);
		CALL.set(null);
		respond(200, context());
	}

	@AfterAll
	static void stopIdentity() {
		IDENTITY.stop(0);
	}

	@Test
	void verifiedBearerUsesIdentityHttpAndExposesOnlyTheVerifiedPrincipal() throws Exception {
		String token = token(KEY, "ecommerce-identity", "ecommerce-api", 4, 300);
		var response = request("POST", PREVIEW, token, null);
		assertThat(response.statusCode()).isEqualTo(200);
		var principal = json.readTree(response.body());
		assertThat(principal.path("userId").asString()).isEqualTo(USER.toString());
		assertThat(principal.path("sessionId").asString()).isEqualTo(SESSION.toString());
		assertThat(principal.path("authzVersion").asLong()).isEqualTo(4);
		assertThat(CALL.get()).isEqualTo(new Call("POST", "/internal/v1/identity/auth/authorization-context",
				"Bearer " + token, "order-outbound-fixture"));
		respond(200, changed("sessionActive", false));
		assertRejected(request("POST", PREVIEW, token, null), 401, "UNAUTHENTICATED");
		assertThat(CALLS).hasValue(2);
	}

	@Test
	void invalidClaimsSignatureAndServiceOnlyCredentialNeverReachIdentityContext() throws Exception {
		assertRejected(request("POST", PREVIEW, null, "inventory-fixture-token"), 401, "UNAUTHENTICATED");
		assertRejected(request("POST", PREVIEW, "not-a-jwt", null), 401, "UNAUTHENTICATED");
		assertRejected(request("POST", PREVIEW, token(rsaKey(), "ecommerce-identity", "ecommerce-api", 4, 300), null),
				401, "UNAUTHENTICATED");
		assertRejected(request("POST", PREVIEW, token(KEY, "wrong-issuer", "ecommerce-api", 4, 300), null),
				401, "UNAUTHENTICATED");
		assertRejected(request("POST", PREVIEW, token(KEY, "ecommerce-identity", "wrong-aud", 4, 300), null),
				401, "UNAUTHENTICATED");
		assertRejected(request("POST", PREVIEW, token(KEY, "ecommerce-identity", "ecommerce-api", 4, -60), null),
				401, "TOKEN_EXPIRED");
		assertThat(CALLS).hasValue(0);
	}

	@Test
	void staleVersionAndMissingPermissionAreRejected() throws Exception {
		String token = token(KEY, "ecommerce-identity", "ecommerce-api", 4, 300);
		respond(200, changed("authzVersion", 5));
		assertRejected(request("POST", PREVIEW, token, null), 401, "TOKEN_STALE");
		respond(401, Map.of("error", Map.of("code", "TOKEN_STALE")));
		assertRejected(request("POST", PREVIEW, token, null), 401, "TOKEN_STALE");
		respond(200, changed("permissions", List.of()));
		assertRejected(request("POST", PREVIEW, token, null), 403, "FORBIDDEN");
	}

	@Test
	void sellerInventoryRequiresItsCurrentApprovedSellerProfile() throws Exception {
		String token = token(KEY, "ecommerce-identity", "ecommerce-api", 4, 300);
		var seller = context();
		seller.put("accountType", "SELLER");
		seller.put("roles", List.of("SELLER"));
		seller.put("permissions", List.of("inventory:manage:own"));
		seller.remove("customerProfile");
		seller.put("sellerProfile", Map.of("status", "ACTIVE", "approvalStatus", "APPROVED"));
		respond(200, seller);
		assertThat(request("GET", INVENTORY, token, null).statusCode()).isEqualTo(200);
		seller.put("sellerProfile", Map.of("status", "ACTIVE", "approvalStatus", "PENDING"));
		respond(200, seller);
		assertRejected(request("GET", INVENTORY, token, null), 403, "FORBIDDEN");
	}

	@Test
	void missingContextFieldsAndUnavailableIdentityFailClosed() throws Exception {
		String token = token(KEY, "ecommerce-identity", "ecommerce-api", 4, 300);
		respond(200, changed("sessionActive", null));
		assertRejected(request("POST", PREVIEW, token, null), 503, "SERVICE_UNAVAILABLE");
		respond(200, changed("userId", UUID.randomUUID()));
		assertRejected(request("POST", PREVIEW, token, null), 503, "SERVICE_UNAVAILABLE");
		respond(500, Map.of());
		assertRejected(request("POST", PREVIEW, token, null), 503, "SERVICE_UNAVAILABLE");
		REPLY.set(new Reply(200, json.writeValueAsString(Map.of("data", context())), 2400));
		assertRejected(request("POST", PREVIEW, token, null), 503, "SERVICE_UNAVAILABLE");
	}

	private void assertRejected(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).isEqualTo(status);
		assertThat(json.readTree(response.body()).path("error").path("code").asString()).isEqualTo(code);
	}

	private HttpResponse<String> request(String method, String path, String bearer, String service) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.timeout(Duration.ofSeconds(10)).method(method, HttpRequest.BodyPublishers.noBody());
		if (bearer != null) {
			request.header("Authorization", "Bearer " + bearer);
		}
		if (service != null) {
			request.header("X-Service-Token", service);
		}
		try (var http = HttpClient.newHttpClient()) {
			return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
	}

	private void respond(int status, Map<String, ?> body) {
		String jsonBody = json.writeValueAsString(status == 200 ? Map.of("data", body) : body);
		REPLY.set(new Reply(status, jsonBody, 0));
	}

	private static Map<String, Object> context() {
		return new HashMap<>(Map.of("userId", USER, "authzVersion", 4, "sessionActive", true, "userStatus", "ACTIVE",
				"accountType", "CUSTOMER", "accountStatus", "ACTIVE", "accessMode", "FULL", "roles", List.of("CUSTOMER"),
				"permissions", List.of("checkout:create:own"), "customerProfile", Map.of("status", "ACTIVE")));
	}

	private static Map<String, Object> changed(String field, Object value) {
		var data = context();
		data.put(field, value);
		return data;
	}

	private static RSAKey rsaKey() {
		try {
			return new RSAKeyGenerator(2048).keyID("identity-test").generate();
		} catch (Exception failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static String token(RSAKey key, String issuer, String audience, long version, long validFor) throws Exception {
		Instant now = Instant.now();
		var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
				new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject(USER.toString())
						.issueTime(Date.from(now.minusSeconds(90))).expirationTime(Date.from(now.plusSeconds(validFor)))
						.claim("sessionId", SESSION.toString()).claim("authzVersion", version).build());
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}

	private static HttpServer startIdentity() {
		try {
			var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/.well-known/jwks.json", exchange ->
					write(exchange, new Reply(200, new JWKSet(KEY.toPublicJWK()).toString(), 0)));
			server.createContext("/internal/v1/identity/auth/authorization-context", exchange -> {
				CALLS.incrementAndGet();
				CALL.set(new Call(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
						exchange.getRequestHeaders().getFirst("Authorization"),
						exchange.getRequestHeaders().getFirst("X-Service-Token")));
				write(exchange, REPLY.get());
			});
			server.start();
			return server;
		} catch (IOException failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static void write(HttpExchange exchange, Reply reply) throws IOException {
		try (exchange) {
			if (reply.delayMillis() > 0) {
				try {
					Thread.sleep(reply.delayMillis());
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					return;
				}
			}
			byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(reply.status(), body.length);
			exchange.getResponseBody().write(body);
		}
	}

	private record Reply(int status, String body, long delayMillis) {
	}
	private record Call(String method, String path, String bearer, String serviceToken) {
	}

	@RestController
	@TestComponent
	static class OrderRouteFixture {
		@PostMapping(PREVIEW)
		CurrentUser preview(@AuthenticationPrincipal CurrentUser user) {
			return user;
		}

		@GetMapping(INVENTORY)
		CurrentUser inventory(@AuthenticationPrincipal CurrentUser user) {
			return user;
		}
	}
}
