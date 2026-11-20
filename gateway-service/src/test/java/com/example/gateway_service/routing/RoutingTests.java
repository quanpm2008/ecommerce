package com.example.gateway_service.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.example.gateway_service.support.GatewayClient;
import com.example.gateway_service.support.StubService;
import com.example.gateway_service.support.StubService.Reply;
import com.example.gateway_service.support.TestTokens;

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
 * Checklist §1: the API §2.2 route table, pass-through of requests and responses, and 404 for everything else. Each
 * service is a {@link StubService} registered through Spring Cloud's simple discovery client (Eureka off), so the
 * real load-balancer path is used; product-service has two instances. Requests carry a valid token (token rules are
 * {@code JwtTests}); unknown paths are 404 even without one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "eureka.client.enabled=false")
class RoutingTests {

	static final Map<String, StubService> SERVICES = Arrays.stream(ServiceRoute.values())
			.map(ServiceRoute::serviceId)
			.collect(Collectors.toMap(Function.identity(), StubService::start));

	static final StubService SECOND_CATALOG = StubService.start("product-service-2");

	static final StubService JWKS = TestTokens.startJwks(TestTokens.KEY);

	static final ObjectMapper JSON = new ObjectMapper();

	@LocalServerPort
	int port;

	/** Signed in, so protected routes are forwarded too. */
	GatewayClient gateway;

	GatewayClient anonymous;

	@DynamicPropertySource
	static void instances(DynamicPropertyRegistry registry) {
		SERVICES.forEach((id, stub) -> registry.add(
				"spring.cloud.discovery.client.simple.instances." + id + "[0].uri", stub::uri));
		registry.add("spring.cloud.discovery.client.simple.instances.product-service[1].uri", SECOND_CATALOG::uri);
		registry.add("gateway.jwt.jwk-set-uri", () -> TestTokens.jwksUri(JWKS));
	}

	@AfterAll
	static void stopStubs() {
		SERVICES.values().forEach(StubService::close);
		SECOND_CATALOG.close();
		JWKS.close();
	}

	@BeforeEach
	void setUp() {
		anonymous = new GatewayClient(port);
		gateway = anonymous.withToken(TestTokens.valid());
		allStubs().forEach(StubService::reset);
	}

	@ParameterizedTest
	@CsvSource({
			"/api/v1/identity/users/me, identity-service",
			"/api/v1/identity/media/upload-urls, identity-service",
			"/api/v1/identity, identity-service",
			"/api/v1/categories, product-service",
			"/api/v1/categories/c1/products, product-service",
			"/api/v1/products/p1, product-service",
			"/api/v1/seller/products/p1/media, product-service",
			"/api/v1/admin/products/p1, product-service",
			"/api/v1/admin/categories, product-service",
			"/api/v1/media/upload-urls, product-service",
			"/api/v1/cart/items, cart-service",
			"/api/v1/orders/o1, order-service",
			"/api/v1/payments/checkout-sessions/s1, payment-service",
			"/api/v1/finance/wallet, finance-service",
			"/api/v1/notifications/unread-count, notification-service" })
	void eachPublicPathReachesItsService(String path, String service) {
		HttpResponse<String> response = gateway.get(path);

		assertThat(response.statusCode()).isEqualTo(200);
		List<StubService> reached = allStubs().filter(stub -> !stub.received().isEmpty()).toList();
		assertThat(reached).hasSize(1);
		assertThat(reached.getFirst().name()).startsWith(service);
		assertThat(reached.getFirst().last().target()).isEqualTo(path);
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/v1/catalog/products", "/api/v1/catalog/internal", "/api/v1/seller/orders",
			"/api/v1/seller/wallet", "/api/v1/admin/users", "/api/v1/admin/sellers", "/api/v1/media/upload-urls/x",
			"/api/v1/unknown", "/api/v1", "/api/v2/identity/users/me", "/identity/users/me", "/products",
			"/.well-known/jwks.json", "/v3/api-docs", "/swagger-ui.html", "/swagger-ui/index.html", "/error", "/" })
	void everythingElseIsANotFoundEnvelope(String path) throws Exception {
		HttpResponse<String> response = anonymous.get(path, "X-Request-Id", "req-404", "Accept", "text/html");

		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/json"));
		JsonNode error = JSON.readTree(response.body()).path("error");
		assertThat(error.path("code").asString()).isEqualTo("NOT_FOUND");
		assertThat(error.path("message").asString()).isNotBlank();
		assertThat(error.path("details").isArray()).isTrue();
		assertThat(error.path("requestId").asString()).isEqualTo("req-404");
		assertThat(allStubs().flatMap(stub -> stub.received().stream())).isEmpty();
	}

	@Test
	void methodQueryAndBodyPassThroughUnchanged() {
		StubService cart = SERVICES.get("cart-service");
		cart.reply(Reply.json(201, "{\"data\":{\"cartItemId\":\"i1\"}}").withHeader("Location", "/api/v1/cart/items/i1"));

		HttpResponse<String> response = gateway.send("POST", "/api/v1/cart/items?source=pdp&tag=a%20b",
				"{\"productId\":\"p1\",\"quantity\":2}", "Content-Type", "application/json",
				"Idempotency-Key", "k-1", "Accept-Language", "vi");

		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(response.body()).isEqualTo("{\"data\":{\"cartItemId\":\"i1\"}}");
		assertThat(response.headers().firstValue("Location")).hasValue("/api/v1/cart/items/i1");
		StubService.Received received = cart.last();
		assertThat(received.method()).isEqualTo("POST");
		assertThat(received.target()).isEqualTo("/api/v1/cart/items?source=pdp&tag=a%20b");
		assertThat(received.body()).isEqualTo("{\"productId\":\"p1\",\"quantity\":2}");
		assertThat(received.header("Content-Type")).startsWith("application/json");
		assertThat(received.header("Idempotency-Key")).isEqualTo("k-1");
		assertThat(received.header("Accept-Language")).isEqualTo("vi");
	}

	@ParameterizedTest
	@ValueSource(strings = { "PUT", "PATCH", "DELETE" })
	void otherMethodsAreForwarded(String method) {
		StubService identity = SERVICES.get("identity-service");

		gateway.send(method, "/api/v1/identity/customers/me/addresses/a1", "{\"isDefault\":true}",
				"Content-Type", "application/json");

		assertThat(identity.last().method()).isEqualTo(method);
		assertThat(identity.last().body()).isEqualTo("{\"isDefault\":true}");
	}

	@Test
	void noContentStaysEmpty() {
		SERVICES.get("notification-service").reply(new Reply(204, null, null, Map.of(), java.time.Duration.ZERO));

		HttpResponse<String> response = gateway.send("DELETE", "/api/v1/notifications/n1", null);

		assertThat(response.statusCode()).isEqualTo(204);
		assertThat(response.body()).isEmpty();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"202|{\"data\":{\"status\":\"PENDING\"}}",
			"400|{\"error\":{\"code\":\"VALIDATION_ERROR\",\"message\":\"m\",\"details\":[{\"field\":\"x\"}],\"requestId\":\"r\"}}",
			"401|{\"error\":{\"code\":\"TOKEN_STALE\",\"message\":\"m\",\"details\":[],\"requestId\":null}}",
			"409|{\"error\":{\"code\":\"INSUFFICIENT_STOCK\",\"message\":\"m\",\"details\":[],\"requestId\":null}}",
			"422|{\"error\":{\"code\":\"PRODUCT_NOT_SELLABLE\",\"message\":\"m\",\"details\":[],\"requestId\":null}}",
			"503|{\"error\":{\"code\":\"REGION_SOURCE_UNAVAILABLE\",\"message\":\"m\",\"details\":[],\"requestId\":null}}" })
	void downstreamStatusAndBodyAreReturnedAsSent(int status, String body) {
		SERVICES.get("payment-service").reply(Reply.json(status, body));

		HttpResponse<String> response = gateway.send("POST", "/api/v1/payments", "{}", "Content-Type",
				"application/json");

		assertThat(response.statusCode()).isEqualTo(status);
		assertThat(response.body()).isEqualTo(body);
	}

	@Test
	void requestsAreSpreadOverInstances() {
		StubService first = SERVICES.get("product-service");

		for (int i = 0; i < 6; i++) {
			assertThat(gateway.get("/api/v1/products").statusCode()).isEqualTo(200);
		}

		assertThat(first.received()).hasSize(3);
		assertThat(SECOND_CATALOG.received()).hasSize(3);
	}

	static Stream<StubService> allStubs() {
		return Stream.concat(SERVICES.values().stream(), Stream.of(SECOND_CATALOG));
	}

}
