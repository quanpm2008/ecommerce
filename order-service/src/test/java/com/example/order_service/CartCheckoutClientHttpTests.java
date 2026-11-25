package com.example.order_service;

import com.example.order_service.client.CartCheckoutItemsClient;
import com.example.order_service.client.CartCheckoutItemsClient.Line;
import com.example.order_service.client.CartCheckoutItemsClient.MissingItems;
import com.example.order_service.client.CartCheckoutItemsClient.Reader;
import com.example.order_service.client.CartCheckoutItemsClient.Unavailable;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
		"eureka.client.enabled=false", "logging.level.root=WARN",
		"order.cart.service-token=" + CartCheckoutClientHttpTests.TOKEN
})
class CartCheckoutClientHttpTests extends OrderPostgresTestSupport {
	static final String TOKEN = "preview-cart-order";
	static final String PATH = "/internal/v1/cart/checkout-items";
	static final JsonMapper JSON = JsonMapper.builder().build();
	static final UUID CUSTOMER = UUID.randomUUID();
	static final UUID FOREIGN_CUSTOMER = UUID.randomUUID();
	static final UUID CART = UUID.randomUUID();
	static final UUID FOREIGN_CART = UUID.randomUUID();
	static final Line FIRST = new Line(UUID.randomUUID(), UUID.randomUUID(), 2, 7);
	static final Line SECOND = new Line(UUID.randomUUID(), UUID.randomUUID(), 1, 0);
	static final Line FOREIGN = new Line(UUID.randomUUID(), UUID.randomUUID(), 5, Long.MAX_VALUE);
	static final List<UUID> SELECTED = List.of(FIRST.cartItemId(), SECOND.cartItemId());
	private static final Fixture CART_HTTP = new Fixture();
	@Autowired
	private Reader reader;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("order.cart.base-url", CART_HTTP::baseUrl);
	}

	@BeforeEach
	void reset() {
		CART_HTTP.reset();
	}

	@AfterAll
	static void stop() {
		CART_HTTP.close();
	}

	@Test
	void orderApplicationRegistersCartCheckoutFeignClient() {
		var registration = OrderServiceApplication.class.getAnnotation(EnableFeignClients.class);
		assertThat(registration.clients()).contains(CartCheckoutItemsClient.class);
	}

	@Test
	void sendsOneCredentialAndReadsTheStoredOwnerOfTheWholeSelectedSet() {
		var snapshot = reader.read(CUSTOMER, SELECTED);
		assertThat(snapshot.cartId()).isEqualTo(CART);
		assertThat(snapshot.customerId()).isEqualTo(CUSTOMER);
		assertThat(snapshot.items()).containsExactly(SECOND, FIRST);
		assertThat(CART_HTTP.lastCall).isEqualTo(new Call("POST", List.of(TOKEN), null, CUSTOMER, SELECTED));
		assertThat(reader.read(FOREIGN_CUSTOMER, List.of(FOREIGN.cartItemId())).items()).containsExactly(FOREIGN);

		assertThatThrownBy(() -> reader.read(CUSTOMER, List.of(FIRST.cartItemId(), FOREIGN.cartItemId())))
				.isInstanceOf(MissingItems.class);
		assertThatThrownBy(() -> reader.read(CUSTOMER, List.of(FIRST.cartItemId(), UUID.randomUUID())))
				.isInstanceOf(MissingItems.class);
		assertThat(CART_HTTP.calls).hasValue(4);
	}

	@Test
	void refusesMalformedAndInconsistentSuccessResponses() {
		assertUnavailable(200, Map.of("data", Map.of("cartId", CART, "items", List.of(FIRST, SECOND))));
		assertUnavailable(200, snapshot(FOREIGN_CUSTOMER, List.of(FIRST, SECOND)));
		assertUnavailable(200, snapshot(CUSTOMER, List.of(FIRST)));
		assertUnavailable(200, snapshot(CUSTOMER, List.of(FIRST, FIRST)));
		assertUnavailable(200, snapshot(CUSTOMER, List.of(FIRST, FOREIGN)));
		for (Object quantity : List.of(0, 100, 1.5, "2", 4294967298L)) {
			assertUnavailable(200, snapshot(CUSTOMER, List.of(FIRST, line(quantity, 0))));
		}
		for (Object version : List.of(-1, 0.5, "0", new java.math.BigInteger("9223372036854775808"))) {
			assertUnavailable(200, snapshot(CUSTOMER, List.of(FIRST, line(1, version))));
		}
		assertUnavailable(201, snapshot(CUSTOMER, List.of(FIRST, SECOND)));
	}

	@Test
	void refusesUnexpectedErrorsAndTimeoutAsUnavailable() {
		assertUnavailable(404, error("OTHER_CODE"));
		assertUnavailable(400, error("VALIDATION_ERROR"));
		assertUnavailable(401, error("UNAUTHENTICATED"));
		assertUnavailable(503, error("SERVICE_UNAVAILABLE"));
		CART_HTTP.pause();
		try {
			assertThatThrownBy(() -> reader.read(CUSTOMER, SELECTED)).isInstanceOf(Unavailable.class);
			assertThat(CART_HTTP.calls).hasValue(5);
		} finally {
			CART_HTTP.resume();
		}
	}

	private void assertUnavailable(int status, Object body) {
		CART_HTTP.reply(status, body);
		assertThatThrownBy(() -> reader.read(CUSTOMER, SELECTED)).isInstanceOf(Unavailable.class);
	}

	static Map<String, ?> snapshot(UUID owner, List<?> items) {
		return Map.of("data", Map.of("cartId", CART, "customerId", owner, "items", items));
	}

	private static Map<String, ?> line(Object quantity, Object version) {
		return Map.of("cartItemId", SECOND.cartItemId(), "productId", SECOND.productId(),
				"quantity", quantity, "rowVersion", version);
	}

	static Map<String, ?> error(String code) {
		return Map.of("error", Map.of("code", code));
	}

	static final class Fixture implements AutoCloseable {
		private final HttpServer server;
		private final Map<UUID, StoredCart> byOwner = Map.of(
				CUSTOMER, new StoredCart(CART, CUSTOMER, List.of(SECOND, FIRST)),
				FOREIGN_CUSTOMER, new StoredCart(FOREIGN_CART, FOREIGN_CUSTOMER, List.of(FOREIGN)));
		private final java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		final AtomicInteger calls = new AtomicInteger();
		volatile Call lastCall;
		private volatile Reply response;
		private volatile CountDownLatch pause;

		Fixture() {
			try {
				server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
				server.setExecutor(executor);
				server.createContext(PATH, this::respond);
				server.start();
			} catch (IOException failure) {
				throw new IllegalStateException(failure);
			}
		}

		String baseUrl() {
			return "http://127.0.0.1:" + server.getAddress().getPort();
		}

		void reset() {
			calls.set(0);
			lastCall = null;
			response = null;
			pause = null;
		}

		void reply(int status, Object body) {
			response = new Reply(status, body);
		}

		void pause() {
			pause = new CountDownLatch(1);
		}

		void resume() {
			pause.countDown();
		}

		private void respond(HttpExchange exchange) throws IOException {
			try (exchange) {
				var request = JSON.readValue(exchange.getRequestBody().readAllBytes(), CartCheckoutItemsClient.Request.class);
				var headers = exchange.getRequestHeaders();
				lastCall = new Call(exchange.getRequestMethod(), headers.get("X-Service-Token"),
						headers.getFirst("Authorization"), request.customerId(), request.cartItemIds());
				calls.incrementAndGet();
				var wait = pause;
				if (wait != null) {
					try {
						wait.await(7, TimeUnit.SECONDS);
					} catch (InterruptedException interrupted) {
						Thread.currentThread().interrupt();
						return;
					}
				}
				Reply reply = response == null ? resolve(lastCall) : response;
				byte[] body = JSON.writeValueAsBytes(reply.body());
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(reply.status(), body.length);
				exchange.getResponseBody().write(body);
			}
		}

		private Reply resolve(Call call) {
			if (!List.of(TOKEN).equals(call.serviceTokens()) || call.bearer() != null) {
				return new Reply(401, error("UNAUTHENTICATED"));
			}
			var cart = byOwner.get(call.customer());
			if (cart != null) {
				var items = cart.items().stream().filter(line -> call.itemIds().contains(line.cartItemId())).toList();
				if (items.size() == call.itemIds().size()) {
					return new Reply(200, Map.of("data", new CartCheckoutItemsClient.Snapshot(cart.cartId(), cart.owner(), items)));
				}
			}
			return new Reply(404, error("NOT_FOUND"));
		}

		@Override
		public void close() {
			server.stop(0);
			executor.close();
		}
	}

	record Call(String method, List<String> serviceTokens, String bearer, UUID customer, List<UUID> itemIds) {}
	private record StoredCart(UUID cartId, UUID owner, List<Line> items) {}
	private record Reply(int status, Object body) {}
}
