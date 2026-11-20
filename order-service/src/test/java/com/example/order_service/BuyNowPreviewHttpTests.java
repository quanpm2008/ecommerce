package com.example.order_service;

import com.example.order_service.checkout.CheckoutQuote;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"eureka.client.enabled=false", "logging.level.root=WARN", "order.identity.service-token=preview-identity",
		"order.product.service-token=preview-product", "order.checkout.quote-key=" + CheckoutQuoteTests.QUOTE_KEY
})
class BuyNowPreviewHttpTests extends OrderPostgresTestSupport {
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final UUID CUSTOMER = UUID.randomUUID();
	private static final UUID PRODUCT = UUID.randomUUID();
	private static final UUID SELLER = UUID.randomUUID();
	private static final UUID ADDRESS = UUID.randomUUID();
	private static final RSAKey KEY = rsa();
	private static final String TOKEN = token();
	private static final String ELIGIBILITY = "/internal/v1/identity/customers/" + CUSTOMER + "/checkout-eligibility";
	private static final String STATUS = "/internal/v1/identity/sellers/status";
	private static final String SELLER_INFO = "/api/v1/identity/sellers/" + SELLER;
	private static final String ADDRESS_BOOK = "/api/v1/identity/customers/me/addresses";
	private static final String PRODUCT_BATCH = "/internal/v1/products/batch";
	private static final Map<String, Call> CALLS = new ConcurrentHashMap<>();
	private static final Map<String, Reply> REPLIES = new ConcurrentHashMap<>();
	private static final HttpServer MOCK = mock();
	@LocalServerPort
	private int port;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private CheckoutQuote quotes;

	@DynamicPropertySource
	static void remotes(DynamicPropertyRegistry registry) {
		registry.add("order.identity.base-url", () -> "http://127.0.0.1:" + MOCK.getAddress().getPort());
		registry.add("order.product.base-url", () -> "http://127.0.0.1:" + MOCK.getAddress().getPort());
	}

	@BeforeEach
	void reset() {
		jdbc.update("delete from orders.checkout_session where customer_id = ?", CUSTOMER);
		jdbc.update("delete from orders.inventory where product_id = ?", PRODUCT);
		jdbc.update("insert into orders.inventory (product_id, seller_id, on_hand_quantity, reserved_quantity) values (?, ?, 7, 2)",
				PRODUCT, SELLER);
		CALLS.clear();
		REPLIES.clear();
		reply("/.well-known/jwks.json", 200, new JWKSet(KEY.toPublicJWK()).toJSONObject());
		reply("/internal/v1/identity/auth/authorization-context", 200, Map.of("data", Map.of("userId", CUSTOMER,
				"accountType", "CUSTOMER", "accountStatus", "ACTIVE", "userStatus", "ACTIVE", "accessMode", "FULL",
				"authzVersion", 4, "sessionActive", true, "roles", List.of("CUSTOMER"),
				"permissions", List.of("checkout:create:own"), "customerProfile", Map.of("status", "ACTIVE"))));
		eligibility(true, 1, false);
		reply(STATUS, 200, Map.of("data", List.of(Map.of("sellerId", SELLER, "sellable", true))));
		reply(SELLER_INFO, 200, Map.of("data", Map.of("sellerId", SELLER, "sellerName", "Example Seller")));
		reply(ADDRESS_BOOK, 200, Map.of("data", List.of(Map.of("addressId", ADDRESS, "recipientName", "Receiver",
				"recipientPhone", "0900000000", "provinceCode", "01", "provinceName", "Province", "wardCode", "00001",
				"wardName", "Ward", "addressDetail", "Address", "isDefault", true, "createdAt", "2026-11-21T00:00:00Z"))));
		reply(PRODUCT_BATCH, 200, Map.of("data", List.of(Map.of("productId", PRODUCT, "sellerId", SELLER,
				"priceVnd", 150000, "productName", "Product", "media", List.of(), "sellable", true))));
	}

	@AfterAll
	static void closeMock() {
		MOCK.stop(0);
	}

	@Test
	void onlyReadsStateAndIssuesAnIndependentFiveMinuteQuoteForEachPreview() throws Exception {
		var before = jdbc.queryForMap("select * from orders.inventory where product_id = ?", PRODUCT);
		JsonNode first = data(2);
		JsonNode second = data(2);
		assertThat(first.path("canPlaceOrder").asBoolean()).isTrue();
		assertThat(first.path("source").asString()).isEqualTo("BUY_NOW");
		assertThat(first.path("shippingFeeVnd").asLong()).isZero();
		assertThat(first.path("totalAmountVnd").asLong()).isEqualTo(300000);
		assertThat(first.path("defaultAddress").path("addressId").asString()).isEqualTo(ADDRESS.toString());
		assertThat(first.at("/orders/0/seller/sellerName").asString()).isEqualTo("Example Seller");
		assertThat(first.at("/orders/0/items/0/quantity").asInt()).isEqualTo(2);
		var firstQuote = quotes.verify(first.path("quoteToken").asString(), CUSTOMER);
		var nextQuote = quotes.verify(second.path("quoteToken").asString(), CUSTOMER);
		assertThat(firstQuote.expiresAt()).isEqualTo(firstQuote.issuedAt().plusSeconds(300));
		assertThat(nextQuote.expiresAt()).isEqualTo(nextQuote.issuedAt().plusSeconds(300));
		assertThat(nextQuote.checkoutIntentId()).isNotEqualTo(firstQuote.checkoutIntentId());
		assertThat(jdbc.queryForMap("select * from orders.inventory where product_id = ?", PRODUCT)).isEqualTo(before);
		assertThat(jdbc.queryForObject("select count(*) from orders.checkout_session where customer_id = ?",
				Integer.class, CUSTOMER)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from orders.stock_movement where product_id = ?",
				Integer.class, PRODUCT)).isZero();
		assertThat(CALLS.get(ADDRESS_BOOK)).isEqualTo(new Call("GET", null, "Bearer " + TOKEN, null, ""));
		assertThat(CALLS.get(SELLER_INFO)).isEqualTo(new Call("GET", null, null, null, ""));
		assertThat(CALLS.get(ELIGIBILITY)).isEqualTo(new Call("GET", null, null, "preview-identity", ""));
		assertThat(CALLS.get(STATUS)).isEqualTo(new Call("GET", "ids=" + SELLER, null, "preview-identity", ""));
		assertThat(CALLS.get(PRODUCT_BATCH).token()).isEqualTo("preview-product");
		assertThat(CALLS.get(PRODUCT_BATCH).method()).isEqualTo("POST");
		assertThat(JSON.readTree(CALLS.get(PRODUCT_BATCH).body())).isEqualTo(JSON.valueToTree(Map.of("productIds", List.of(PRODUCT))));
	}

	@Test
	void unavailableStockAndQuantityLimitNeverIssueAQuote() throws Exception {
		JsonNode stock = data(6);
		assertThat(stock.at("/issues/0/code").asString()).isEqualTo("INSUFFICIENT_STOCK");
		assertThat(stock.at("/issues/0/availableQuantity").asInt()).isEqualTo(5);
		assertBlocked(stock);
		assertThat(data(100).at("/issues/0/code").asString()).isEqualTo("QUANTITY_LIMIT_EXCEEDED");
		jdbc.update("delete from orders.inventory where product_id = ?", PRODUCT);
		assertThat(data(1).at("/issues/0/availableQuantity").asInt()).isZero();
	}

	@Test
	void keepsDeterminateSellerIssueSeparateFromUpstreamFailure() throws Exception {
		reply(STATUS, 200, Map.of("data", List.of(Map.of("sellerId", SELLER, "sellable", false))));
		JsonNode notSellable = data(1);
		assertThat(notSellable.at("/issues/0/code").asString()).isEqualTo("PRODUCT_NOT_SELLABLE");
		assertBlocked(notSellable);
		reply(STATUS, 500, Map.of());
		assertThat(request(1, TOKEN).statusCode()).isEqualTo(503);
		reply(PRODUCT_BATCH, 500, Map.of());
		assertThat(request(1, TOKEN).statusCode()).isEqualTo(503);
		assertThat(request(1, null).statusCode()).isEqualTo(401);
	}

	@Test
	void profilePaymentAndPendingBlockersHaveNoQuote() throws Exception {
		eligibility(false, 0, true);
		reply(ADDRESS_BOOK, 200, Map.of("data", List.of()));
		UUID session = UUID.randomUUID();
		jdbc.update("""
				insert into orders.checkout_session (checkout_session_id, checkout_intent_id, customer_id, checkout_source,
				idempotency_key, request_hash, total_amount_vnd, currency, status, address_id, created_at, expires_at)
				values (?, ?, ?, 'BUY_NOW', 'pending-preview-test', 'hash', 1, 'VND', 'AWAITING_PAYMENT', ?, now(), now()+interval '15 minutes')
				""", session, UUID.randomUUID(), CUSTOMER, ADDRESS);
		JsonNode blocked = data(1);
		assertBlocked(blocked);
		assertThat(blocked.at("/blockers/0/code").asString()).isEqualTo("CUSTOMER_PAYMENT_BLOCKED");
		assertThat(blocked.at("/blockers/1/code").asString()).isEqualTo("PROFILE_INCOMPLETE");
		assertThat(blocked.at("/blockers/2/code").asString()).isEqualTo("CHECKOUT_PENDING_EXISTS");
		assertThat(blocked.at("/blockers/2/checkoutSessionId").asString()).isEqualTo(session.toString());
	}

	private static void assertBlocked(JsonNode response) {
		assertThat(response.path("quoteToken").isNull()).isTrue();
		assertThat(response.path("canPlaceOrder").asBoolean()).isFalse();
	}

	private JsonNode data(int quantity) throws Exception {
		var response = request(quantity, TOKEN);
		assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
		return JSON.readTree(response.body()).path("data");
	}

	private HttpResponse<String> request(int quantity, String bearer) throws Exception {
		String body = JSON.writeValueAsString(Map.of("source", "BUY_NOW", "buyNowItem", Map.of("productId", PRODUCT, "quantity", quantity)));
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/orders/checkout/preview"))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
		if (bearer != null) {
			request.header("Authorization", "Bearer " + bearer);
		}
		try (var client = HttpClient.newHttpClient()) {
			return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
	}

	private static void eligibility(boolean phone, int addresses, boolean paymentBlocked) {
		reply(ELIGIBILITY, 200, Map.of("data", Map.of("userId", CUSTOMER, "userStatus", "ACTIVE", "customerStatus", "ACTIVE",
				"paymentBlocked", paymentBlocked, "hasPhone", phone, "addressCount", addresses,
				"canCheckout", phone && addresses > 0 && !paymentBlocked, "authzVersion", 4)));
	}

	private static void reply(String path, int status, Map<String, ?> data) {
		REPLIES.put(path, new Reply(status, JSON.writeValueAsBytes(data)));
	}

	private static HttpServer mock() {
		try {
			var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", BuyNowPreviewHttpTests::respond);
			server.start();
			return server;
		} catch (IOException failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static void respond(HttpExchange exchange) throws IOException {
		try (exchange) {
			String path = exchange.getRequestURI().getPath();
			CALLS.put(path, new Call(exchange.getRequestMethod(), exchange.getRequestURI().getRawQuery(),
					exchange.getRequestHeaders().getFirst("Authorization"), exchange.getRequestHeaders().getFirst("X-Service-Token"),
					new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
			Reply reply = REPLIES.getOrDefault(path, new Reply(404, new byte[0]));
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(reply.status(), reply.body().length);
			exchange.getResponseBody().write(reply.body());
		}
	}

	private static RSAKey rsa() {
		try {
			return new RSAKeyGenerator(2048).keyID("preview-test").generate();
		} catch (Exception failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static String token() {
		try {
			Instant now = Instant.now();
			var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),
					new JWTClaimsSet.Builder().issuer("ecommerce-identity").audience("ecommerce-api").subject(CUSTOMER.toString())
							.issueTime(Date.from(now.minusSeconds(1))).expirationTime(Date.from(now.plusSeconds(3600)))
							.claim("sessionId", UUID.randomUUID().toString()).claim("authzVersion", 4).build());
			token.sign(new RSASSASigner(KEY));
			return token.serialize();
		} catch (Exception failure) {
			throw new IllegalStateException(failure);
		}
	}

	private record Reply(int status, byte[] body) {
	}

	private record Call(String method, String query, String bearer, String token, String body) {
	}
}
