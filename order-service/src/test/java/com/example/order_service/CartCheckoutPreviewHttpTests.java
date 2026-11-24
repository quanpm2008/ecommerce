package com.example.order_service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.example.order_service.CartCheckoutClientHttpTests.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"eureka.client.enabled=false", "logging.level.root=WARN", "order.identity.service-token=preview-identity",
		"order.product.service-token=preview-product", "order.cart.service-token=" + TOKEN,
		"order.checkout.quote-key=" + CheckoutQuoteTests.QUOTE_KEY
})
class CartCheckoutPreviewHttpTests extends OrderPostgresTestSupport {
	private static final UUID SELLER_ONE = UUID.randomUUID();
	private static final UUID SELLER_TWO = UUID.randomUUID();
	private static final UUID ADDRESS = UUID.randomUUID();
	private static final String PRODUCT_PATH = "/internal/v1/products/batch";
	private static final RSAKey SIGNING_KEY = rsa();
	private static final String BEARER = userToken();
	private static final Fixture CART_HTTP = new Fixture();
	private static final Map<String, Object> REPLIES = new ConcurrentHashMap<>();
	private static final AtomicInteger PRODUCT_CALLS = new AtomicInteger();
	private static final HttpServer REMOTES = remotes();
	private static volatile JsonNode productRequest;
	@LocalServerPort
	private int port;
	@Autowired
	private JdbcTemplate jdbc;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("order.cart.base-url", CART_HTTP::baseUrl);
		registry.add("order.identity.base-url", () -> "http://127.0.0.1:" + REMOTES.getAddress().getPort());
		registry.add("order.product.base-url", () -> "http://127.0.0.1:" + REMOTES.getAddress().getPort());
	}

	@BeforeEach
	void reset() {
		CART_HTTP.reset();
		PRODUCT_CALLS.set(0);
		productRequest = null;
		REPLIES.clear();
		REPLIES.put("/.well-known/jwks.json", new JWKSet(SIGNING_KEY.toPublicJWK()).toJSONObject());
		REPLIES.put("/internal/v1/identity/auth/authorization-context", Map.of("data", Map.of("userId", CUSTOMER,
				"accountType", "CUSTOMER", "accountStatus", "ACTIVE", "userStatus", "ACTIVE", "accessMode", "FULL",
				"authzVersion", 4, "sessionActive", true, "roles", List.of("CUSTOMER"),
				"permissions", List.of("checkout:create:own"), "customerProfile", Map.of("status", "ACTIVE"))));
		REPLIES.put("/internal/v1/identity/customers/" + CUSTOMER + "/checkout-eligibility",
				Map.of("data", Map.of("userId", CUSTOMER, "userStatus", "ACTIVE", "customerStatus", "ACTIVE",
						"paymentBlocked", false, "hasPhone", true, "addressCount", 1, "canCheckout", true, "authzVersion", 4)));
		REPLIES.put("/api/v1/identity/customers/me/addresses", Map.of("data", List.of(Map.of(
				"addressId", ADDRESS, "recipientName", "Receiver", "recipientPhone", "0900000000", "provinceCode", "01",
				"provinceName", "Province", "wardCode", "00001", "wardName", "Ward", "addressDetail", "Address",
				"isDefault", true, "createdAt", "2026-11-21T00:00:00Z"))));
		REPLIES.put("/api/v1/identity/sellers/" + SELLER_ONE, Map.of("data", Map.of("sellerId", SELLER_ONE, "sellerName", "First seller")));
		REPLIES.put("/api/v1/identity/sellers/" + SELLER_TWO, Map.of("data", Map.of("sellerId", SELLER_TWO, "sellerName", "Second seller")));
		REPLIES.put(PRODUCT_PATH, Map.of("data", List.of(product(SECOND.productId(), SELLER_TWO, "Second", 450000),
				product(FIRST.productId(), SELLER_ONE, "First", 150000))));
		jdbc.update("delete from orders.checkout_session where customer_id = ?", CUSTOMER);
		stock(FIRST.productId(), SELLER_ONE);
		stock(SECOND.productId(), SELLER_TWO);
	}

	@AfterAll
	static void stop() {
		CART_HTTP.close();
		REMOTES.stop(0);
	}

	@Test
	void groupsTheSelectedLinesBySellerAndOnlyReadsState() throws Exception {
		var inventoryBefore = inventory();
		JsonNode data = data(request(cart(SELECTED)));
		assertThat(data.path("source").asString()).isEqualTo("CART");
		assertThat(data.path("canPlaceOrder").asBoolean()).isTrue();
		assertThat(data.path("quoteToken").asString()).isNotBlank();
		assertThat(data.path("totalAmountVnd").asLong()).isEqualTo(750000);
		assertThat(data.path("shippingFeeVnd").asLong()).isZero();
		assertThat(data.at("/defaultAddress/addressId").asString()).isEqualTo(ADDRESS.toString());
		assertThat(data.path("orders").size()).isEqualTo(2);
		for (JsonNode group : data.path("orders")) {
			boolean first = group.at("/seller/sellerId").asString().equals(SELLER_ONE.toString());
			assertThat(group.at("/seller/sellerName").asString()).isEqualTo(first ? "First seller" : "Second seller");
			assertThat(group.at("/items/0/productId").asString()).isEqualTo((first ? FIRST : SECOND).productId().toString());
			assertThat(group.at("/items/0/quantity").asInt()).isEqualTo(first ? 2 : 1);
			assertThat(group.path("orderTotalVnd").asLong()).isEqualTo(first ? 300000 : 450000);
		}
		assertThat(CART_HTTP.calls).hasValue(1);
		assertThat(CART_HTTP.lastCall).isEqualTo(new Call("POST", List.of(TOKEN), null, CUSTOMER, SELECTED));
		assertThat(PRODUCT_CALLS).hasValue(1);
		assertThat(productRequest.path("productIds").values()).containsExactlyInAnyOrder(
				JSON.valueToTree(FIRST.productId().toString()), JSON.valueToTree(SECOND.productId().toString()));
		assertThat(inventory()).isEqualTo(inventoryBefore);
		assertNoCheckoutOrMovement();
	}

	@Test
	void rejectsInvalidPayloadBeforeCallingCart() throws Exception {
		assertError(request(cart(List.of())), 400, "VALIDATION_ERROR");
		assertError(request(cart(List.of(FIRST.cartItemId(), FIRST.cartItemId()))), 400, "VALIDATION_ERROR");
		assertError(request(cart(Stream.generate(UUID::randomUUID).limit(51).toList())), 400, "VALIDATION_ERROR");
		assertError(request(cart(List.of("invalid UUID"))), 400, "VALIDATION_ERROR");
		assertError(request(Map.of("source", "CART", "cartItemIds", SELECTED,
				"buyNowItem", Map.of("productId", FIRST.productId(), "quantity", 1))), 400, "VALIDATION_ERROR");
		assertThat(CART_HTTP.calls).hasValue(0);
		assertError(request(cart(Stream.generate(UUID::randomUUID).limit(50).toList())), 404, "NOT_FOUND");
		assertThat(CART_HTTP.calls).hasValue(1);
		assertNoCheckoutOrMovement();
	}

	@Test
	void separatesMissingOrForeignLinesFromUnavailableCartData() throws Exception {
		assertError(request(cart(List.of(FOREIGN.cartItemId()))), 404, "NOT_FOUND");
		assertError(request(cart(List.of(UUID.randomUUID()))), 404, "NOT_FOUND");
		CART_HTTP.reply(200, Map.of("data", Map.of("cartId", CART, "items", List.of(FIRST, SECOND))));
		assertError(request(cart(SELECTED)), 503, "SERVICE_UNAVAILABLE");
		CART_HTTP.reply(200, snapshot(FOREIGN_CUSTOMER, List.of(FIRST, SECOND)));
		assertError(request(cart(SELECTED)), 503, "SERVICE_UNAVAILABLE");
		CART_HTTP.reply(200, snapshot(CUSTOMER, List.of(FIRST, FIRST)));
		assertError(request(cart(SELECTED)), 503, "SERVICE_UNAVAILABLE");
		CART_HTTP.pause();
		try {
			assertError(request(cart(SELECTED)), 503, "SERVICE_UNAVAILABLE");
		} finally {
			CART_HTTP.resume();
		}
		assertThat(PRODUCT_CALLS).hasValue(0);
		assertNoCheckoutOrMovement();
	}

	@Test
	void returnsLineIssuesForInsufficientStockAnd503ForAnIncompleteProductBatch() throws Exception {
		jdbc.update("update orders.inventory set on_hand_quantity = 1 where product_id = ?", FIRST.productId());
		var issue = data(request(cart(SELECTED)));
		assertThat(issue.path("canPlaceOrder").asBoolean()).isFalse();
		assertThat(issue.path("quoteToken").isNull()).isTrue();
		assertThat(issue.at("/issues/0/code").asString()).isEqualTo("INSUFFICIENT_STOCK");
		assertThat(issue.at("/issues/0/cartItemId").asString()).isEqualTo(FIRST.cartItemId().toString());
		assertThat(issue.at("/issues/0/availableQuantity").asInt()).isEqualTo(1);
		REPLIES.put(PRODUCT_PATH, Map.of("data", List.of(product(FIRST.productId(), SELLER_ONE, "First", 150000))));
		assertError(request(cart(SELECTED)), 503, "SERVICE_UNAVAILABLE");
		assertNoCheckoutOrMovement();
	}

	private void stock(UUID product, UUID seller) {
		jdbc.update("delete from orders.inventory where product_id = ?", product);
		jdbc.update("insert into orders.inventory (product_id, seller_id, on_hand_quantity, reserved_quantity) values (?, ?, 5, 0)",
				product, seller);
	}

	private List<Map<String, Object>> inventory() {
		return jdbc.queryForList("select * from orders.inventory where product_id in (?, ?) order by product_id",
				FIRST.productId(), SECOND.productId());
	}

	private void assertNoCheckoutOrMovement() {
		assertThat(jdbc.queryForObject("select count(*) from orders.checkout_session where customer_id = ?",
				Integer.class, CUSTOMER)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from orders.stock_movement where product_id in (?, ?)",
				Integer.class, FIRST.productId(), SECOND.productId())).isZero();
	}

	private HttpResponse<String> request(Map<String, ?> body) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/orders/checkout/preview"))
				.header("Authorization", "Bearer " + BEARER).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
		try (var client = HttpClient.newHttpClient()) {
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

	private static JsonNode data(HttpResponse<String> response) {
		assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
		return JSON.readTree(response.body()).path("data");
	}

	private static void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
		assertThat(JSON.readTree(response.body()).at("/error/code").asString()).isEqualTo(code);
	}

	private static Map<String, ?> cart(List<?> ids) {
		return Map.of("source", "CART", "cartItemIds", ids);
	}

	private static Map<String, ?> product(UUID product, UUID seller, String name, long price) {
		return Map.of("productId", product, "sellerId", seller, "priceVnd", price, "productName", name,
				"media", List.of(), "sellable", true);
	}

	private static HttpServer remotes() {
		try {
			var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", exchange -> {
				try (exchange) {
					String path = exchange.getRequestURI().getPath();
					Object response = REPLIES.get(path);
					if (PRODUCT_PATH.equals(path)) {
						PRODUCT_CALLS.incrementAndGet();
						productRequest = JSON.readTree(exchange.getRequestBody().readAllBytes());
					} else if ("/internal/v1/identity/sellers/status".equals(path)) {
						UUID seller = UUID.fromString(exchange.getRequestURI().getQuery().substring(4));
						response = Map.of("data", List.of(Map.of("sellerId", seller, "sellable", true)));
					}
					byte[] bytes = JSON.writeValueAsBytes(response);
					exchange.getResponseHeaders().set("Content-Type", "application/json");
					exchange.sendResponseHeaders(response == null ? 404 : 200, bytes.length);
					exchange.getResponseBody().write(bytes);
				}
			});
			server.start();
			return server;
		} catch (IOException error) {
			throw new IllegalStateException(error);
		}
	}

	private static RSAKey rsa() {
		try {
			return new RSAKeyGenerator(2048).keyID("cart-preview").generate();
		} catch (Exception error) {
			throw new IllegalStateException(error);
		}
	}

	private static String userToken() {
		try {
			Instant now = Instant.now();
			var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(SIGNING_KEY.getKeyID()).build(),
					new JWTClaimsSet.Builder().issuer("ecommerce-identity").audience("ecommerce-api").subject(CUSTOMER.toString())
							.issueTime(Date.from(now.minusSeconds(1))).expirationTime(Date.from(now.plusSeconds(3600)))
							.claim("sessionId", UUID.randomUUID().toString()).claim("authzVersion", 4).build());
			token.sign(new RSASSASigner(SIGNING_KEY));
			return token.serialize();
		} catch (Exception error) {
			throw new IllegalStateException(error);
		}
	}
}
