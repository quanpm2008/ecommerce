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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BuyNowPayPostgresTests extends PayHttpTestSupport {
	@Test
	void createsOnePendingCheckoutWithIndependentHoldDeadlineAndDurableWork() throws Exception {
		var sample = sample(8, 3);
		String quote = quote(sample);
		CLOCK.advance(Duration.ofMinutes(4));
		var reply = pay(sample.customer(), UUID.randomUUID(), quote, sample.address());
		assertThat(reply.statusCode()).isEqualTo(202);
		var data = body(reply).path("data");
		UUID session = UUID.fromString(data.path("checkoutSessionId").asString());
		assertThat(data.path("status").asString()).isEqualTo("AWAITING_PAYMENT");
		assertThat(data.path("paymentUrl").isNull()).isTrue();
		assertThat(data.path("totalAmountVnd").asLong()).isEqualTo(3000);
		assertThat(jdbc.queryForObject("""
				select extract(epoch from expires_at-created_at)::int from orders.checkout_session where checkout_session_id = ?
				""", Integer.class, session)).isEqualTo(900);
		assertThat(jdbc.queryForObject("select reserved_quantity from orders.inventory where product_id = ?", Integer.class,
				sample.product())).isEqualTo(3);
		assertThat(jdbc.queryForList("select command_type from orders.checkout_outbox where checkout_session_id = ?", String.class,
				session)).containsExactlyInAnyOrder("CreateProviderSession", "ExpireCheckout");
		assertThat(jdbc.queryForObject("select commission_bps_snapshot from orders.purchase_order where checkout_session_id = ?",
				Integer.class, session)).isEqualTo(500);
		assertThat(jdbc.queryForObject("select count(*) from orders.reservation r join orders.order_item i using(order_item_id)"
				+ " join orders.purchase_order o using(order_id) where o.checkout_session_id = ? and r.status = 'HELD'",
				Integer.class, session)).isEqualTo(1);
		assertThat(lastTouch.get()).isEqualTo(session);
		assertThat(touchSawCommit.get()).isEqualTo(Boolean.TRUE);
	}

	@Test
	void rejectsExpiredAndInvalidQuoteWithoutWriting() throws Exception {
		var sample = sample(8, 2);
		String quote = quote(sample);
		assertRejected(pay(sample.customer(), UUID.randomUUID(), "bogus", sample.address()), 400, "CHECKOUT_QUOTE_INVALID");
		CLOCK.advance(Duration.ofMinutes(5));
		assertRejected(pay(sample.customer(), UUID.randomUUID(), quote, sample.address()), 409, "CHECKOUT_QUOTE_EXPIRED");
		assertNoCheckout(sample);
	}

	@Test
	void rejectsChangedPriceInvalidAddressAndUnavailableInventoryBeforeCommit() throws Exception {
		var sample = sample(2, 3);
		String quote = quote(sample);
		PRICES.put(sample.product(), 2000L);
		assertRejected(pay(sample.customer(), UUID.randomUUID(), quote, sample.address()), 409, "CHECKOUT_QUOTE_CHANGED");
		PRICES.put(sample.product(), 1000L);
		assertRejected(pay(sample.customer(), UUID.randomUUID(), quote, UUID.randomUUID()), 404, "NOT_FOUND");
		assertRejected(pay(sample.customer(), UUID.randomUUID(), quote, sample.address()), 409, "CHECKOUT_ITEMS_INVALID");
		assertNoCheckout(sample);
	}
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"eureka.client.enabled=false", "logging.level.root=WARN", "order.identity.service-token=test-order-pay",
		"order.product.service-token=test-order-pay", "order.finance.service-token=test-order-pay"
})
@Import(PayHttpTestSupport.ClockConfiguration.class)
abstract class PayHttpTestSupport extends OrderPostgresTestSupport {
	static final JsonMapper JSON = JsonMapper.builder().build();
	static final UUID SELLER = UUID.randomUUID();
	static final Map<UUID, Long> PRICES = new ConcurrentHashMap<>();
	static final Map<UUID, UUID> ADDRESSES = new ConcurrentHashMap<>();
	static final AtomicReference<UUID> lastTouch = new AtomicReference<>();
	static final AtomicReference<Boolean> touchSawCommit = new AtomicReference<>();
	static final AtomicInteger touchStatus = new AtomicInteger(200);
	static final AtomicInteger financeStatus = new AtomicInteger(200);
	static final AtomicInteger checkoutSourceCalls = new AtomicInteger();
	static final AtomicInteger authCalls = new AtomicInteger();
	static final AtomicReference<UUID> revokedCustomer = new AtomicReference<>();
	static final FixtureClock CLOCK = new FixtureClock();
	private static final RSAKey KEY = signingKey();
	private static final HttpServer SERVER = startServer();

	@LocalServerPort
	int port;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	CheckoutQuote quotes;

	@DynamicPropertySource
	static void sources(DynamicPropertyRegistry config) {
		String endpoint = "http://127.0.0.1:" + SERVER.getAddress().getPort();
		config.add("order.identity.base-url", () -> endpoint);
		config.add("order.product.base-url", () -> endpoint);
		config.add("order.finance.base-url", () -> endpoint);
		config.add("order.checkout.quote-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
	}

	@BeforeEach
	void resetPayFixtures() {
		CLOCK.now.set(Instant.parse("2026-11-21T00:00:00Z"));
		lastTouch.set(null);
		touchSawCommit.set(null);
		touchStatus.set(200);
		financeStatus.set(200);
		checkoutSourceCalls.set(0);
		authCalls.set(0);
		revokedCustomer.set(null);
	}

	Sample sample(int stock, int quantity) {
		var sample = new Sample(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), quantity);
		PRICES.put(sample.product(), 1000L);
		ADDRESSES.put(sample.address(), sample.customer());
		jdbc.update("insert into orders.inventory(product_id, seller_id, on_hand_quantity) values (?, ?, ?)",
				sample.product(), SELLER, stock);
		return sample;
	}

	String quote(Sample sample) {
		return quotes.issue(sample.customer(), sample.product(), SELLER, sample.quantity(), 1000L);
	}

	HttpResponse<String> pay(UUID customer, UUID key, String quote, UUID address) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/orders/checkout/pay"))
				.header("Authorization", "Bearer " + token(customer)).header("Idempotency-Key", key.toString())
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
						JSON.writeValueAsString(Map.of("quoteToken", quote, "addressId", address)))).build();
		try (var client = HttpClient.newHttpClient()) {
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

	void assertNoCheckout(Sample sample) {
		assertThat(jdbc.queryForObject("select count(*) from orders.checkout_session where customer_id = ?", Integer.class,
				sample.customer())).isZero();
		assertThat(jdbc.queryForObject("select reserved_quantity from orders.inventory where product_id = ?", Integer.class,
				sample.product())).isZero();
		assertThat(lastTouch.get()).isNull();
	}

	static JsonNode body(HttpResponse<String> response) {
		return JSON.readTree(response.body());
	}

	static void assertRejected(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		assertThat(body(response).path("error").path("code").asString()).isEqualTo(code);
	}

	private static HttpServer startServer() {
		try {
			var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/.well-known/jwks.json", x -> respond(x, 200, new JWKSet(KEY.toPublicJWK()).toJSONObject()));
			server.createContext("/internal/v1/identity/auth/authorization-context", PayHttpTestSupport::authorize);
			server.createContext("/internal/v1/identity/customers/", PayHttpTestSupport::customerData);
			server.createContext("/internal/v1/identity/sellers/status", x -> data(x, List.of(Map.of("sellerId", SELLER, "sellable", true))));
			server.createContext("/api/v1/identity/sellers/", x -> data(x, Map.of("sellerId", SELLER, "sellerName", "Shop test")));
			server.createContext("/internal/v1/products/batch", PayHttpTestSupport::productData);
			server.createContext("/internal/v1/finance/commission-rate", x -> {
				if (financeStatus.get() == 200) {
					data(x, Map.of("commissionBps", 500));
				} else {
					respond(x, financeStatus.get(), Map.of());
				}
			});
			server.start();
			Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> server.stop(0)));
			return server;
		} catch (IOException failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static void authorize(HttpExchange exchange) throws IOException {
		authCalls.incrementAndGet();
		try {
			String userId = SignedJWT.parse(exchange.getRequestHeaders().getFirst("Authorization").substring(7))
					.getJWTClaimsSet().getSubject();
			respond(exchange, 200, Map.of("data", Map.of("userId", userId, "accountType", "CUSTOMER", "accountStatus",
					"ACTIVE", "userStatus", "ACTIVE", "accessMode", "FULL", "authzVersion", 1, "roles", List.of("CUSTOMER"),
					"permissions", List.of("checkout:create:own"), "sessionActive", !UUID.fromString(userId).equals(revokedCustomer.get()),
					"customerProfile", Map.of("status", "ACTIVE"))));
		} catch (java.text.ParseException failure) {
			throw new IOException(failure);
		}
	}

	private static void customerData(HttpExchange x) throws IOException {
		String[] path = x.getRequestURI().getPath().split("/");
		UUID customer = UUID.fromString(path[5]);
		if (path[6].equals("checkout-eligibility")) {
			data(x, Map.of("userId", customer, "userStatus", "ACTIVE", "customerStatus", "ACTIVE", "paymentBlocked", false,
					"hasPhone", true, "addressCount", 1, "canCheckout", true, "authzVersion", 1));
		} else if (path.length == 9 && path[8].equals("touch")) {
			UUID session = UUID.fromString(JSON.readTree(x.getRequestBody()).path("checkoutSessionId").asString());
			lastTouch.set(session);
			try (var connection = DriverManager.getConnection(System.getenv("ORDER_TEST_DATABASE_URL"),
					System.getenv("ORDER_TEST_DATABASE_USER"), System.getenv("ORDER_TEST_DATABASE_PASSWORD"));
					var query = connection.prepareStatement("select count(*) from orders.checkout_session where checkout_session_id = ?")) {
				query.setObject(1, session);
				try (var result = query.executeQuery()) {
					touchSawCommit.set(result.next() && result.getLong(1) == 1);
				}
			} catch (java.sql.SQLException failure) {
				throw new IOException(failure);
			}
			respond(x, touchStatus.get(), Map.of());
		} else if (customer.equals(ADDRESSES.get(UUID.fromString(path[7])))) {
			data(x, Map.of("recipientName", "Khách thử", "recipientPhone", "0900000000", "provinceCode", "01",
					"provinceName", "Tỉnh thử", "wardCode", "001", "wardName", "Phường thử", "addressDetail", "Địa chỉ thử"));
		} else {
			respond(x, 404, Map.of());
		}
	}

	private static void productData(HttpExchange x) throws IOException {
		var output = new ArrayList<>();
		for (var input : JSON.readTree(x.getRequestBody()).path("productIds")) {
			UUID product = UUID.fromString(input.asString());
			Long price = PRICES.get(product);
			if (price != null) {
				output.add(Map.of("productId", product, "sellerId", SELLER, "productName", "Sản phẩm thử", "media", List.of(),
						"priceVnd", price, "coverImageUrl", "https://example.invalid/test.png", "sellable", true));
			}
		}
		data(x, output);
	}

	private static void data(HttpExchange x, Object body) throws IOException {
		checkoutSourceCalls.incrementAndGet();
		respond(x, 200, Map.of("data", body));
	}

	private static void respond(HttpExchange x, int status, Object body) throws IOException {
		try (x) {
			byte[] bytes = JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
			x.getResponseHeaders().set("Content-Type", "application/json");
			x.sendResponseHeaders(status, bytes.length);
			x.getResponseBody().write(bytes);
		}
	}

	private static String token(UUID user) throws Exception {
		var claims = new JWTClaimsSet.Builder().subject(user.toString()).issuer("ecommerce-identity")
				.audience("ecommerce-api").expirationTime(Date.from(Instant.now().plusSeconds(120)))
				.claim("sessionId", UUID.randomUUID().toString()).claim("authzVersion", 1).build();
		var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
		jwt.sign(new RSASSASigner(KEY));
		return jwt.serialize();
	}

	private static RSAKey signingKey() {
		try {
			return new RSAKeyGenerator(2048).keyID("pay-fixture").generate();
		} catch (com.nimbusds.jose.JOSEException failure) {
			throw new IllegalStateException(failure);
		}
	}

	record Sample(UUID customer, UUID product, UUID address, int quantity) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ClockConfiguration {
		@Bean Clock checkoutClock() { return CLOCK; }
	}

	static class FixtureClock extends Clock {
		private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-21T00:00:00Z"));
		void advance(Duration duration) {
			now.updateAndGet(value -> value.plus(duration));
		}
		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
		@Override public Instant instant() { return now.get(); }
	}
}
