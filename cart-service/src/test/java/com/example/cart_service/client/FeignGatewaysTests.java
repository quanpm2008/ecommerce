package com.example.cart_service.client;

import com.example.cart_service.IntegrationTestSupport;
import com.example.cart_service.common.error.ApiException;
import com.example.cart_service.common.error.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The real Feign gateways against a local HTTP stub that answers with the JSON shapes Identity (openapi.json),
 * product-service and the API §7.1 inventory contract use: paths, headers, envelope decoding, unknown fields and
 * error mapping.
 */
class FeignGatewaysTests extends IntegrationTestSupport {

	private static final String SERVICE_TOKEN = "cart-service-test-token-0123456789abcdef";

	private static final UUID USER = UUID.randomUUID();

	private static final UUID SHOP = UUID.randomUUID();

	private static final UUID BANNED_SHOP = UUID.randomUUID();

	private static final UUID PRODUCT = UUID.randomUUID();

	private static final Map<String, String> lastHeaders = new ConcurrentHashMap<>();

	private static final HttpServer stub = start();

	@Autowired
	private FeignGateways.Identity identity;

	@Autowired
	private FeignGateways.Product products;

	@Autowired
	private FeignGateways.Inventory inventory;

	@DynamicPropertySource
	static void urls(DynamicPropertyRegistry registry) {
		String base = "http://127.0.0.1:" + stub.getAddress().getPort();
		registry.add("cart.clients.identity-url", () -> base);
		registry.add("cart.clients.product-url", () -> base);
		registry.add("cart.clients.order-url", () -> base);
		registry.add("cart.internal.service-token", () -> SERVICE_TOKEN);
	}

	@AfterAll
	static void stop() {
		stub.stop(0);
	}

	@Test
	void authorizationContextSendsBothTokensAndDecodesIdentitysShape() {
		AuthorizationContext context = identity.authorizationContext("good");
		assertThat(context.userId()).isEqualTo(USER);
		assertThat(context.userStatus()).isEqualTo("ACTIVE");
		assertThat(context.sessionActive()).isTrue();
		assertThat(context.permissions()).contains("cart:manage:own");
		assertThat(context.customerProfile().status()).isEqualTo("ACTIVE");
		assertThat(context.customerProfile().paymentBlocked()).isFalse();
		assertThat(lastHeaders.get("authorization")).isEqualTo("Bearer good");
		assertThat(lastHeaders.get("x-service-token")).isEqualTo(SERVICE_TOKEN);
	}

	@Test
	void identity401CodesArePassedOnAndOtherFailuresAre503() {
		assertThatThrownBy(() -> identity.authorizationContext("stale"))
				.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.TOKEN_STALE));
		assertThatThrownBy(() -> identity.authorizationContext("expired"))
				.isInstanceOfSatisfying(ApiException.class,
						ex -> assertThat(ex.code()).isEqualTo(ErrorCode.TOKEN_EXPIRED));
		assertThatThrownBy(() -> identity.authorizationContext("broken"))
				.isInstanceOfSatisfying(ApiException.class,
						ex -> assertThat(ex.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
	}

	@Test
	void sellerStatusesAndNames() {
		assertThat(identity.sellable(List.of(SHOP, BANNED_SHOP)))
				.containsExactlyInAnyOrderEntriesOf(Map.of(SHOP, true, BANNED_SHOP, false));
		assertThat(identity.sellerNames(List.of(SHOP, BANNED_SHOP))).containsExactlyEntriesOf(Map.of(SHOP,
				"Shop Áo Xinh"));
	}

	@Test
	void productBatchAndAvailability() {
		Map<UUID, ProductSnapshot> found = products.products(List.of(PRODUCT));
		assertThat(found.get(PRODUCT).priceVnd()).isEqualTo(150_000L);
		assertThat(found.get(PRODUCT).sellerId()).isEqualTo(SHOP);
		assertThat(found.get(PRODUCT).selling()).isTrue();
		assertThat(inventory.availability(List.of(PRODUCT))).containsExactlyEntriesOf(Map.of(PRODUCT, 48));
		assertThat(lastHeaders.get("x-service-token")).isEqualTo(SERVICE_TOKEN);
	}

	private static HttpServer start() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", FeignGatewaysTests::route);
			server.start();
			return server;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static void route(HttpExchange exchange) throws IOException {
		exchange.getRequestHeaders().forEach((name, values) -> lastHeaders.put(name.toLowerCase(), values.get(0)));
		String path = exchange.getRequestURI().getPath();
		String auth = exchange.getRequestHeaders().getFirst("Authorization");
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		if (path.equals("/internal/v1/identity/auth/authorization-context")) {
			switch (auth) {
				case "Bearer good" -> reply(exchange, 200, """
						{"data":{"userId":"%s","userStatus":"ACTIVE","authzVersion":3,"roles":["CUSTOMER"],
						"permissions":["cart:manage:own","checkout:create:own"],
						"customerProfile":{"status":"ACTIVE","accessMode":"FULL","paymentBlocked":false},
						"sellerProfile":null,"sessionActive":true}}""".formatted(USER));
				case "Bearer stale" -> reply(exchange, 401, error("TOKEN_STALE"));
				case "Bearer expired" -> reply(exchange, 401, error("TOKEN_EXPIRED"));
				default -> reply(exchange, 500, error("INTERNAL_ERROR"));
			}
		}
		else if (path.equals("/internal/v1/identity/sellers/status")) {
			reply(exchange, 200, """
					{"data":[{"sellerId":"%s","userId":"%s","userStatus":"ACTIVE","approvalStatus":"APPROVED",
					"status":"ACTIVE","sellerRoleActive":true,"sellable":true},
					{"sellerId":"%s","userId":"%s","userStatus":"ACTIVE","approvalStatus":"APPROVED",
					"status":"BANNED","sellerRoleActive":true,"sellable":false}]}"""
					.formatted(SHOP, SHOP, BANNED_SHOP, BANNED_SHOP));
		}
		else if (path.equals("/api/v1/identity/sellers/" + SHOP)) {
			reply(exchange, 200, """
					{"data":{"sellerId":"%s","sellerName":"Shop Áo Xinh","description":null,"logoUrl":null}}"""
					.formatted(SHOP));
		}
		else if (path.equals("/internal/v1/products/batch") && body.trim().startsWith("[")) {
			reply(exchange, 200, """
					{"data":[{"productId":"%s","sellerId":"%s","priceVnd":150000,"productName":"Áo thun",
					"coverImageUrl":"https://cdn/x.jpg","media":[{"objectKey":"k","type":"IMAGE"}],
					"status":"SELLING","sellable":true}]}""".formatted(PRODUCT, SHOP));
		}
		else if (path.equals("/internal/v1/inventory/availability")) {
			reply(exchange, 200, """
					{"data":[{"productId":"%s","availableQuantity":48}]}""".formatted(PRODUCT));
		}
		else {
			reply(exchange, 404, error("NOT_FOUND"));
		}
	}

	private static String error(String code) {
		return "{\"error\":{\"code\":\"" + code + "\",\"message\":\"x\",\"details\":[],\"requestId\":null}}";
	}

	private static void reply(HttpExchange exchange, int status, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

}
