package com.example.order_service;

import com.example.order_service.client.CartCheckoutItemsClient.Line;
import com.example.order_service.client.CartCheckoutItemsClient.Request;
import com.example.order_service.client.CartCheckoutItemsClient.Snapshot;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Import(CartCheckoutPayPostgresTests.RemoteConfiguration.class)
class CartCheckoutPayPostgresTests extends PayHttpTestSupport {
	private static final UUID SECOND_SELLER = UUID.randomUUID();
	private static final String CART_TOKEN = "test-cart-from-order";
	private static final Map<UUID, Snapshot> CARTS = new ConcurrentHashMap<>();
	private static final Map<UUID, UUID> PRODUCT_SELLERS = new ConcurrentHashMap<>();
	private static final ConcurrentLinkedQueue<Request> CART_CALLS = new ConcurrentLinkedQueue<>();
	private static final AtomicReference<Reply> CART_REPLY = new AtomicReference<>();
	private static final List<String> TABLES = List.of("checkout_session", "purchase_order", "order_item",
			"reservation", "idempotency_record", "checkout_outbox", "stock_movement");
	private static final String PAY_FIXTURE_URL = payFixtureUrl();
	private static final HttpClient SUPPORT_HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
	private static final java.util.concurrent.ExecutorService REMOTE_THREADS = Executors.newVirtualThreadPerTaskExecutor();
	private static final HttpServer REMOTES = startRemotes();

	@DynamicPropertySource
	static void cartSources(DynamicPropertyRegistry properties) {
		properties.add("order.cart.base-url", CartCheckoutPayPostgresTests::remoteUrl);
		properties.add("order.cart.service-token", () -> CART_TOKEN);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class RemoteConfiguration {
		@Bean
		DynamicPropertyRegistrar fixtureOverrides() {
			return properties -> {
				properties.add("order.product.base-url", CartCheckoutPayPostgresTests::remoteUrl);
				properties.add("order.identity.base-url", CartCheckoutPayPostgresTests::remoteUrl);
			};
		}
	}

	private static String remoteUrl() {
		return "http://127.0.0.1:" + REMOTES.getAddress().getPort();
	}

	@BeforeEach
	void resetCartFixture() {
		CARTS.clear();
		PRODUCT_SELLERS.clear();
		CART_CALLS.clear();
		CART_REPLY.set(null);
	}

	@AfterAll
	static void stopCartFixture() {
		REMOTES.stop(0);
		REMOTE_THREADS.close();
		SUPPORT_HTTP.close();
	}

	@Test
	void commitsCartSnapshot() throws Exception {
		var cart = cart();
		var first = cart.first();
		var snapshot = cart.snapshot();
		String quote = preview(cart);
		CLOCK.advance(Duration.ofMinutes(4));
		var response = pay(first.customer(), UUID.randomUUID(), quote, first.address());
		assertThat(response.statusCode()).as(response.body()).isEqualTo(202);
		var data = body(response).path("data");
		UUID sessionId = UUID.fromString(data.path("checkoutSessionId").asString());
		assertThat(data.path("source").asString()).isEqualTo("CART");
		assertThat(data.path("totalAmountVnd").asLong()).isEqualTo(3500);
		assertThat(data.path("orders").size()).isEqualTo(2);
		assertThat(CART_CALLS).hasSize(2).allSatisfy(request -> {
			assertThat(request.customerId()).isEqualTo(first.customer());
			assertThat(request.cartItemIds()).containsExactlyInAnyOrderElementsOf(cart.itemIds());
		});
		assertThat(jdbc.queryForObject("select source_cart_id from orders.checkout_session where checkout_session_id = ?",
				UUID.class, sessionId)).isEqualTo(snapshot.cartId());
		String persisted = jdbc.queryForObject("select cart_snapshot::text from orders.checkout_session where checkout_session_id = ?",
				String.class, sessionId);
		assertThat(JSON.readTree(persisted).values())
				.containsExactlyInAnyOrderElementsOf(JSON.readTree(JSON.writeValueAsBytes(snapshot.items())).values());
		assertThat(jdbc.queryForObject("""
				select extract(epoch from expires_at-created_at)::int from orders.checkout_session where checkout_session_id = ?
				""", Integer.class, sessionId)).isEqualTo(900);
		assertThat(jdbc.queryForList("select seller_id from orders.purchase_order where checkout_session_id = ?",
				UUID.class, sessionId)).containsExactlyInAnyOrder(SELLER, SECOND_SELLER);
		assertThat(jdbc.queryForList("select commission_bps_snapshot from orders.purchase_order where checkout_session_id = ?",
				Integer.class, sessionId)).containsExactly(500, 500);
		assertThat(jdbc.queryForList("""
				select i.source_cart_item_id from orders.reservation r join orders.order_item i using (order_item_id)
				join orders.purchase_order o using (order_id) join orders.checkout_session c using (checkout_session_id)
				where c.checkout_session_id = ? and r.expires_at = c.expires_at and r.status = 'HELD' and r.quantity = i.quantity
				""", UUID.class, sessionId)).containsExactlyInAnyOrderElementsOf(cart.itemIds());
		assertThat(jdbc.queryForList("select command_type from orders.checkout_outbox where checkout_session_id = ?",
				String.class, sessionId)).containsExactlyInAnyOrder("CreateProviderSession", "ExpireCheckout");
		assertThat(jdbc.queryForObject("select sum(reserved_quantity)::int from orders.inventory where product_id in (?, ?)",
				Integer.class, cart.first().product(), cart.second().product())).isEqualTo(3);
		assertThat(lastTouch.get()).isEqualTo(sessionId);
		assertThat(touchSawCommit.get()).isTrue();
	}

	@Test
	void rollsBackAllCartHoldsIfTheLaterProductSellsOutAfterPreview() throws Exception {
		var cart = cart();
		String quote = preview(cart);
		UUID depleted = cart.first().product().compareTo(cart.second().product()) > 0
				? cart.first().product() : cart.second().product();
		jdbc.update("update orders.inventory set on_hand_quantity = 0 where product_id = ?", depleted);
		var counts = counts();
		var stock = inventory(cart);
		assertRejected(pay(cart.first().customer(), UUID.randomUUID(), quote, cart.first().address()), 409, "CHECKOUT_ITEMS_INVALID");
		assertThat(counts()).isEqualTo(counts);
		assertThat(inventory(cart)).isEqualTo(stock);
		assertThat(lastTouch.get()).isNull();
	}

	@Test
	void replaysCommittedIntent() throws Exception {
		var cart = sameSellerCart();
		var customer = cart.first().customer();
		var address = cart.first().address();
		var key = UUID.randomUUID();
		var alias = UUID.randomUUID();
		String quote = preview(cart);
		var checkout = pay(customer, key, quote, address);
		assertThat(checkout.statusCode()).as(checkout.body()).isEqualTo(202);
		var original = body(checkout).path("data");
		var session = UUID.fromString(original.path("checkoutSessionId").asString());

		CLOCK.advance(Duration.ofMinutes(5));
		CARTS.remove(customer);
		ADDRESSES.remove(address);
		PRICES.remove(cart.first().product());
		financeStatus.set(503);
		lastTouch.set(null);
		var before = inventory(cart);
		int cartCalls = CART_CALLS.size();
		for (UUID retry : List.of(key, alias)) {
			var response = pay(customer, retry, quote, address);
			assertThat(response.statusCode()).as(response.body()).isEqualTo(202);
			assertThat(response.headers().firstValue("Idempotent-Replayed")).contains("true");
			assertThat(body(response).path("data")).isEqualTo(original);
		}
		assertThat(inventory(cart)).isEqualTo(before);
		assertRejected(pay(customer, key, quote, UUID.randomUUID()), 409, "IDEMPOTENCY_KEY_REUSED");
		assertRejected(pay(customer, UUID.randomUUID(), quote, UUID.randomUUID()), 409, "CHECKOUT_INTENT_CONFLICT");
		assertRejected(pay(UUID.randomUUID(), key, quote, address), 400, "CHECKOUT_QUOTE_INVALID");
		revokedCustomer.set(customer);
		assertRejected(pay(customer, key, quote, address), 401, "UNAUTHENTICATED");
		revokedCustomer.set(null);

		jdbc.update("update orders.checkout_session set status = 'CLOSED', close_reason = 'EXPIRED' where checkout_session_id = ?", session);
		jdbc.update("update orders.purchase_order set status = 'EXPIRED' where checkout_session_id = ?", session);
		jdbc.update("update orders.reservation set status = 'RELEASED', resolved_at = now() where product_id in (?, ?)",
				cart.first().product(), cart.second().product());
		jdbc.update("update orders.inventory set reserved_quantity = 0 where product_id in (?, ?)",
				cart.first().product(), cart.second().product());
		var released = inventory(cart);
		var closed = pay(customer, alias, quote, address);
		assertThat(closed.statusCode()).as(closed.body()).isEqualTo(200);
		var closedData = body(closed).path("data");
		assertThat(closedData.path("checkoutSessionId")).isEqualTo(original.path("checkoutSessionId"));
		assertThat(closedData.path("shippingAddress")).isEqualTo(original.path("shippingAddress"));
		assertThat(closedData.path("expiresAt")).isEqualTo(original.path("expiresAt"));
		assertThat(closedData.path("status").asString()).isEqualTo("CLOSED");
		assertThat(closedData.path("paymentUrl").isNull()).isTrue();
		assertThat(inventory(cart)).isEqualTo(released);
		assertThat(CART_CALLS).hasSize(cartCalls);
		assertThat(lastTouch.get()).isNull();
		assertThat(jdbc.queryForList("select idempotency_key from orders.idempotency_record where result_ref = ?",
				String.class, session)).containsExactlyInAnyOrder(key.toString(), alias.toString());
	}

	@Test
	void racingCartPaysShareOnlyOneCheckout() throws Exception {
		var cart = cart();
		var customer = cart.first().customer();
		var address = cart.first().address();
		String quote = preview(cart);
		var keys = List.of(UUID.randomUUID(), UUID.randomUUID());
		var start = new java.util.concurrent.CountDownLatch(1);
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var futures = keys.stream().map(key -> executor.submit(() -> {
				start.await();
				return pay(customer, key, quote, address);
			})).toList();
			start.countDown();
			var first = futures.getFirst().get(15, java.util.concurrent.TimeUnit.SECONDS);
			var second = futures.getLast().get(15, java.util.concurrent.TimeUnit.SECONDS);
			assertThat(List.of(first, second)).extracting(HttpResponse::statusCode).containsExactly(202, 202);
			var firstId = body(first).at("/data/checkoutSessionId");
			assertThat(body(second).at("/data/checkoutSessionId")).isEqualTo(firstId);
			assertThat(List.of(first, second).stream().filter(r -> r.headers().firstValue("Idempotent-Replayed").isPresent()).count())
					.isEqualTo(1);
			assertThat(jdbc.queryForObject("select count(*) from orders.checkout_session where customer_id = ?", Integer.class, customer))
					.isEqualTo(1);
			assertThat(jdbc.queryForObject("select count(*) from orders.idempotency_record where result_ref = ?", Integer.class,
					UUID.fromString(firstId.asString()))).isEqualTo(2);
			assertThat(jdbc.queryForObject("select sum(reserved_quantity)::int from orders.inventory where product_id in (?, ?)",
					Integer.class, cart.first().product(), cart.second().product())).isEqualTo(3);
		}
	}

	@Test
	void rejectsChangedOrMissingCartWithoutWriting() throws Exception {
		var cart = cart();
		var customer = cart.first().customer();
		String quote = preview(cart);
		var before = counts();
		var stock = inventory(cart);
		var snapshot = cart.snapshot();
		var first = snapshot.items().getFirst();
		var changedVersion = new Line(first.cartItemId(), first.productId(), first.quantity(), first.rowVersion() + 2);
		CARTS.put(customer, new Snapshot(snapshot.cartId(), customer, List.of(changedVersion, snapshot.items().getLast())));
		assertRejected(pay(customer, UUID.randomUUID(), quote, cart.first().address()), 409, "CHECKOUT_QUOTE_CHANGED");
		CARTS.put(customer, new Snapshot(UUID.randomUUID(), customer, snapshot.items()));
		assertRejected(pay(customer, UUID.randomUUID(), quote, cart.first().address()), 409, "CHECKOUT_QUOTE_CHANGED");
		CARTS.remove(customer);
		assertRejected(pay(customer, UUID.randomUUID(), quote, cart.first().address()), 409, "CHECKOUT_QUOTE_CHANGED");
		assertThat(counts()).isEqualTo(before);
		assertThat(inventory(cart)).isEqualTo(stock);
	}

	@Test
	void rejectsUnverifiedCartWithoutWriting() throws Exception {
		var cart = cart();
		var customer = cart.first().customer();
		String quote = preview(cart);
		var before = counts();
		var stock = inventory(cart);
		var snapshot = cart.snapshot();
		for (Object invalid : List.of(
				new Snapshot(snapshot.cartId(), UUID.randomUUID(), snapshot.items()),
				new Snapshot(null, customer, snapshot.items()),
				new Snapshot(snapshot.cartId(), customer, List.of(snapshot.items().getFirst(), snapshot.items().getFirst())))) {
			CART_REPLY.set(new Reply(200, Map.of("data", invalid)));
			assertRejected(pay(customer, UUID.randomUUID(), quote, cart.first().address()), 503, "SERVICE_UNAVAILABLE");
		}
		CART_REPLY.set(new Reply(503, Map.of("error", Map.of("code", "SERVICE_UNAVAILABLE"))));
		assertRejected(pay(customer, UUID.randomUUID(), quote, cart.first().address()), 503, "SERVICE_UNAVAILABLE");
		assertThat(counts()).isEqualTo(before);
		assertThat(inventory(cart)).isEqualTo(stock);
	}

	@Test
	void rejectsPriceChangeAndExpiredFreshQuoteWithoutWriting() throws Exception {
		var cart = cart();
		var customer = cart.first().customer();
		String quote = preview(cart);
		var before = counts();
		var stock = inventory(cart);
		PRICES.put(cart.second().product(), 1600L);
		assertRejected(pay(customer, UUID.randomUUID(), quote, cart.first().address()), 409, "CHECKOUT_QUOTE_CHANGED");
		CLOCK.advance(Duration.ofMinutes(5));
		int cartCalls = CART_CALLS.size();
		assertRejected(pay(customer, UUID.randomUUID(), quote, cart.first().address()), 409, "CHECKOUT_QUOTE_EXPIRED");
		assertThat(CART_CALLS).hasSize(cartCalls);
		assertThat(counts()).isEqualTo(before);
		assertThat(inventory(cart)).isEqualTo(stock);
	}

	private CartCase sameSellerCart() {
		var cart = cart();
		PRODUCT_SELLERS.put(cart.second().product(), SELLER);
		jdbc.update("update orders.inventory set seller_id = ? where product_id = ?", SELLER, cart.second().product());
		var items = cart.snapshot().items().stream().sorted(Comparator.comparing(line -> line.productId().toString())).toList();
		var smallestProduct = items.getFirst();
		var largestProduct = items.getLast();
		var snapshot = new Snapshot(cart.snapshot().cartId(), cart.first().customer(), List.of(
				new Line(UUID.fromString("f0000000-0000-0000-0000-000000000001"),
						smallestProduct.productId(), smallestProduct.quantity(), smallestProduct.rowVersion()),
				new Line(UUID.fromString("00000000-0000-0000-0000-000000000002"),
						largestProduct.productId(), largestProduct.quantity(), largestProduct.rowVersion())));
		CARTS.put(cart.first().customer(), snapshot);
		return new CartCase(cart.first(), cart.second(), snapshot);
	}

	private CartCase cart() {
		var first = sample(8, 2);
		var second = sample(8, 1);
		UUID cartId = UUID.randomUUID();
		var snapshot = new Snapshot(cartId, first.customer(), List.of(
				new Line(UUID.randomUUID(), second.product(), second.quantity(), 9),
				new Line(UUID.randomUUID(), first.product(), first.quantity(), 7)));
		CARTS.put(first.customer(), snapshot);
		PRICES.put(second.product(), 1500L);
		PRODUCT_SELLERS.put(first.product(), SELLER);
		PRODUCT_SELLERS.put(second.product(), SECOND_SELLER);
		jdbc.update("update orders.inventory set seller_id = ? where product_id = ?", SECOND_SELLER, second.product());
		return new CartCase(first, second, snapshot);
	}

	private String preview(CartCase cart) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/orders/checkout/preview"))
				.header("Authorization", "Bearer " + token(cart.first().customer())).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("source", "CART", "cartItemIds", cart.itemIds()))))
				.build();
		var response = SUPPORT_HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		String quote = body(response).at("/data/quoteToken").asString();
		assertThat(quote).isNotBlank();
		return quote;
	}

	private Map<String, Integer> counts() {
		return TABLES.stream().collect(Collectors.toMap(table -> table,
				table -> jdbc.queryForObject("select count(*) from orders." + table, Integer.class)));
	}

	private List<Map<String, Object>> inventory(CartCase cart) {
		return jdbc.queryForList("select * from orders.inventory where product_id in (?, ?) order by product_id",
				cart.first().product(), cart.second().product());
	}

	private static String payFixtureUrl() {
		var endpoint = new AtomicReference<String>();
		PayHttpTestSupport.sources((name, value) -> {
			if ("order.identity.base-url".equals(name)) {
				endpoint.set((String) value.get());
			}
		});
		return endpoint.get();
	}

	private static HttpServer startRemotes() {
		try {
			var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.setExecutor(REMOTE_THREADS);
			server.createContext("/internal/v1/cart/checkout-items", CartCheckoutPayPostgresTests::cartResponse);
			server.createContext("/internal/v1/products/batch", CartCheckoutPayPostgresTests::productResponse);
			server.createContext("/internal/v1/identity/sellers/status", exchange -> {
				UUID seller = UUID.fromString(exchange.getRequestURI().getQuery().substring(4));
				data(exchange, List.of(Map.of("sellerId", seller, "sellable", true)));
			});
			server.createContext("/api/v1/identity/sellers/", exchange -> {
				String path = exchange.getRequestURI().getPath();
				UUID seller = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
				data(exchange, Map.of("sellerId", seller, "sellerName", "Seller " + seller));
			});
			server.createContext("/api/v1/identity/customers/me/addresses", CartCheckoutPayPostgresTests::addressBook);
			server.createContext("/", CartCheckoutPayPostgresTests::delegate);
			server.start();
			return server;
		} catch (IOException error) {
			throw new IllegalStateException(error);
		}
	}

	private static void cartResponse(HttpExchange exchange) throws IOException {
		if (!"POST".equals(exchange.getRequestMethod()) || !List.of(CART_TOKEN).equals(exchange.getRequestHeaders().get("X-Service-Token"))
				|| exchange.getRequestHeaders().getFirst("Authorization") != null) {
			respond(exchange, 401, Map.of("error", Map.of("code", "UNAUTHENTICATED")));
			return;
		}
		var request = JSON.readValue(exchange.getRequestBody(), Request.class);
		CART_CALLS.add(request);
		var reply = CART_REPLY.get();
		if (reply != null) {
			respond(exchange, reply.status(), reply.body());
			return;
		}
		var cart = CARTS.get(request.customerId());
		List<Line> items = cart == null ? List.of()
				: cart.items().stream().filter(line -> request.cartItemIds().contains(line.cartItemId())).toList();
		if (cart == null || items.size() != request.cartItemIds().size()) {
			respond(exchange, 404, Map.of("error", Map.of("code", "NOT_FOUND")));
			return;
		}
		data(exchange, new Snapshot(cart.cartId(), cart.customerId(), items));
	}

	private static void productResponse(HttpExchange exchange) throws IOException {
		var products = new ArrayList<>();
		for (var input : JSON.readTree(exchange.getRequestBody()).path("productIds")) {
			UUID product = UUID.fromString(input.asString());
			if (PRICES.containsKey(product)) {
				products.add(Map.of("productId", product, "sellerId", PRODUCT_SELLERS.get(product), "productName", "Product " + product,
						"priceVnd", PRICES.get(product), "media", List.of(), "coverImageUrl", "https://example.invalid/cart.png", "sellable", true));
			}
		}
		data(exchange, products);
	}

	private static void addressBook(HttpExchange exchange) throws IOException {
		try {
			String token = exchange.getRequestHeaders().getFirst("Authorization").substring(7);
			UUID customer = UUID.fromString(com.nimbusds.jwt.SignedJWT.parse(token).getJWTClaimsSet().getSubject());
			var address = ADDRESSES.entrySet().stream().filter(entry -> entry.getValue().equals(customer)).findFirst();
			data(exchange, address.map(entry -> List.of(Map.of("addressId", entry.getKey(), "recipientName", "Receiver",
					"recipientPhone", "0900000000", "provinceCode", "01", "provinceName", "Province", "wardCode", "00001",
					"wardName", "Ward", "addressDetail", "Address", "isDefault", true, "createdAt", "2026-11-21T00:00:00Z")))
					.orElse(List.of()));
		} catch (java.text.ParseException invalid) {
			throw new IOException(invalid);
		}
	}

	private static void delegate(HttpExchange exchange) throws IOException {
		try (exchange) {
			var request = HttpRequest.newBuilder(URI.create(PAY_FIXTURE_URL + exchange.getRequestURI()))
					.timeout(Duration.ofSeconds(2)).method(exchange.getRequestMethod(),
							HttpRequest.BodyPublishers.ofByteArray(exchange.getRequestBody().readAllBytes()));
			for (String header : List.of("Authorization", "Content-Type", "X-Service-Token")) {
				String value = exchange.getRequestHeaders().getFirst(header);
				if (value != null) {
					request.header(header, value);
				}
			}
			var response = SUPPORT_HTTP.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(response.statusCode(), response.body().length);
			exchange.getResponseBody().write(response.body());
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IOException(interrupted);
		}
	}

	private record CartCase(Sample first, Sample second, Snapshot snapshot) {
		List<UUID> itemIds() {
			return snapshot.items().stream().map(Line::cartItemId).toList();
		}
	}

	private record Reply(int status, Object body) {}
}
