package com.example.order_service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.JsonNode;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.order_service.Phase2LocalBrokerSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class Phase2LocalBrokerIT {
	@Test
	@Timeout(300)
	void settlesPaidCheckoutAndRefundsBothExpiryDeliveryOrdersOnLiveBrokers() throws Exception {
		try (var stack = new Phase2LocalBrokerSupport(); var mock = new SaleFixture()) {
			stack.infrastructure();
			stack.startApplications(mock.origin());
			approveSeller(stack, mock.seller);

			var paid = checkout(stack, mock, false);
			mock.evidence.set(null);
			UUID paidResult = capture(stack, paid.payment());
			deliverWebhook(stack, paidResult);
			waitFor("durable checkout work after evidence HTTP 503", 25, () -> stack.db("order", """
					select count(*) from orders.checkout_sale_evidence
					where checkout_session_id = ? and last_error = 'HISTORICAL_SALE_EVIDENCE_UNAVAILABLE'
					""", paid.checkout()).equals("1"));
			assertThat(status(stack, paid, "AWAITING_PAYMENT")).isTrue();
			assertThat(stack.db("finance", "select count(*) from finance.settlement where order_id=?", paid.order())).isEqualTo("0");
			stack.restartOrder(mock.origin());
			mock.evidence.set(Boolean.TRUE);
			waitFor("settlement from checkout.paid after Order restart", 40, () -> "PENDING".equals(stack.db("finance",
					"select status from finance.settlement where order_id=?", paid.order())));
			String paidWire = event(stack, stack.orderTopic, paid.checkout(), "checkout.paid");
			String capturedAt = JSON.readTree(paidWire).path("payload").path("paidAt").asString();
			assertThat(mock.observedCaptures).contains(capturedAt);
			assertThat(stack.db("order", """
					select count(*) from orders.stock_movement m join orders.order_item i using(order_item_id)
					where i.order_id=? and m.movement_type='SOLD'
					""", paid.order())).isEqualTo("1");
			assertThat(stack.db("order", "select on_hand_quantity || ':' || reserved_quantity from orders.inventory where product_id=?",
					paid.product())).isEqualTo("3:0");
			assertThat(stack.db("finance", "select seller_amount_vnd || ':' || commission_amount_vnd from finance.settlement where order_id=?",
					paid.order())).isEqualTo("950:50");
			deliverWebhook(stack, paidResult);
			stack.awaitCommitted("finance", stack.send(stack.orderTopic, paid.checkout().toString(), paidWire));
			assertSingleSettlement(stack, paid);
			String beforeExpiry = financeSnapshot(stack, mock.seller);

			mock.evidence.set(Boolean.FALSE);
			expireAndRefund(stack, mock, true);
			expireAndRefund(stack, mock, false);
			assertSingleSettlement(stack, paid);
			assertThat(financeSnapshot(stack, mock.seller)).isEqualTo(beforeExpiry);
		}
	}

	private static void assertSingleSettlement(Phase2LocalBrokerSupport stack, Checkout paid) throws Exception {
		assertThat(status(stack, paid, "PAID")).isTrue();
		assertThat(stack.db("finance", "select count(*) from finance.settlement where order_id=? and payment_id=?",
				paid.order(), paid.payment())).isEqualTo("1");
		assertThat(stack.db("finance", """
				select count(*) from finance.wallet_transaction w join finance.settlement s using(settlement_id)
				where s.order_id=? and transaction_type='PAYMENT_RECEIVED'
				""", paid.order())).isEqualTo("2");
	}

	private static String financeSnapshot(Phase2LocalBrokerSupport stack, UUID seller) throws Exception {
		return stack.db("finance", """
				select concat_ws(':', (select count(*) from finance.settlement),
				   (select count(*) from finance.wallet_transaction),
				   (select pending_balance_vnd from finance.wallet where owner_id=?),
				   (select pending_balance_vnd from finance.wallet where owner_type='PLATFORM'))
				""", seller);
	}

	private static void expireAndRefund(Phase2LocalBrokerSupport stack, SaleFixture mock, boolean resultBeforeClose) throws Exception {
		var session = checkout(stack, mock, true);
		UUID result = capture(stack, session.payment());
		if (resultBeforeClose) {
			deliverWebhook(stack, result);
			waitFor("capture before close", 8, () -> "SUCCESS".equals(stack.db("payment",
					"select status from payment_service.payments where payment_id=?", session.payment())));
		}
		waitFor("original SQS expiry and Payment closure", 70, () -> status(stack, session, "CLOSED")
				&& stack.db("payment", """
						select count(*) from payment_service.payment_checkout_guard
						where checkout_session_id=? and closed_at is not null
						""", session.checkout()).equals("1"));
		if (!resultBeforeClose) {
			assertThat(stack.db("payment", "select status from payment_service.payments where payment_id=?",
					session.payment())).isEqualTo("PENDING");
			deliverWebhook(stack, result);
		}
		waitFor("sole successful full-checkout refund", 24, () -> "1".equals(stack.db("payment", """
				select count(*) from payment_service.refunds where payment_id=? and scope='FULL_CHECKOUT' and status='SUCCESS'
				""", session.payment())));
		String closure = event(stack, stack.orderTopic, session.checkout(), "checkout.closed");
		var closed = JSON.readTree(closure).path("payload");
		assertThat(closed.path("closeReason").asString()).isEqualTo("EXPIRED");

		String commandId = stack.db("order", """
				select command_id from orders.checkout_outbox where command_type='ExpireCheckout' and checkout_session_id=?
				and delivered_at is not null
				""", session.checkout());
		assertThat(commandId).isNotBlank();
		stack.sqs.sendMessage(r -> r.queueUrl(stack.queue).messageBody(JSON.writeValueAsString(Map.of(
				"commandId", commandId, "checkoutSessionId", session.checkout(), "expiresAt", session.expiresAt()))));
		stack.awaitCommitted("payment", stack.send(stack.orderTopic, session.checkout().toString(), closure));
		deliverWebhook(stack, result);
		UUID refund = UUID.fromString(stack.db("payment",
				"select refund_id from payment_service.refunds where payment_id=?", session.payment()));
		var refundPayload = JSON.readTree(event(stack, stack.paymentTopic, refund, "refund.succeeded")).path("payload");
		assertThat(refundPayload.path("scope").asString()).isEqualTo("FULL_CHECKOUT");
		assertThat(refundPayload.path("amountVnd").asInt()).isEqualTo(1000);
		assertThat(stack.db("payment", """
				select count(*) from payment_service.outbox_events where aggregate_id=? and event_type='payment.succeeded'
				""", session.payment())).isEqualTo(resultBeforeClose ? "1" : "0");
		assertThat(stack.db("order", """
				select on_hand_quantity || ':' || reserved_quantity from orders.inventory where product_id=?
				""", session.product())).isEqualTo("4:0");
		assertThat(stack.db("order", """
				select count(*) from orders.checkout_event_outbox where checkout_session_id=?
				""", session.checkout())).isEqualTo("1");
		assertThat(stack.db("payment", """
				select count(*) from payment_service.refunds where payment_id=?
				""", session.payment())).isEqualTo("1");
		assertThat(stack.db("payment", """
				select count(*) from payment_service.payment_refund_attempts a
				join payment_service.refunds r using(refund_id) where r.payment_id=?
				""", session.payment())).isEqualTo("1");
		assertThat(stack.db("finance", "select count(*) from finance.settlement where order_id=?",
				session.order())).isEqualTo("0");
		assertThat(stack.call(stack.origin("order").resolve("/api/v1/orders/checkout-sessions/" + session.checkout()),
				"GET", null, Map.of("Authorization", session.authorization()), 200).path("data").path("paymentUrl").isNull()).isTrue();
	}

	private static Checkout checkout(Phase2LocalBrokerSupport stack, SaleFixture mock, boolean shortExpiry) throws Exception {
		UUID product = UUID.randomUUID();
		UUID customer = UUID.randomUUID();
		mock.register(product, customer);
		String authorization = mock.authorization(customer);
		stack.call(stack.origin("order").resolve("/internal/v1/inventory"), "POST",
				Map.of("productId", product, "sellerId", mock.seller, "onHandQuantity", 4),
				Map.of("X-Service-Token", PRODUCT_TO_ORDER), 201);
		var preview = stack.call(stack.origin("order").resolve("/api/v1/orders/checkout/preview"), "POST",
				Map.of("source", "BUY_NOW", "buyNowItem", Map.of("productId", product, "quantity", 1)),
				Map.of("Authorization", authorization), 200).path("data");
		assertThat(preview.path("canPlaceOrder").asBoolean()).isTrue();
		String quote = preview.path("quoteToken").asString();
		assertThat(quote).isNotBlank();
		String key = UUID.randomUUID().toString();
		if (shortExpiry) stack.clockOffset(-870);
		JsonNode session;
		try {
			session = stack.call(stack.origin("order").resolve("/api/v1/orders/checkout/pay"), "POST",
					Map.of("quoteToken", quote, "addressId", mock.address),
					Map.of("Authorization", authorization, "Idempotency-Key", key), 202).path("data");
		} finally {
			stack.clockOffset(0);
		}
		UUID id = id(session, "checkoutSessionId");
		UUID order = id(session.path("orders").get(0), "orderId");
		assertThat(stack.db("order", """
				select extract(epoch from expires_at-created_at)::int from orders.checkout_session where checkout_session_id=?
				""", id)).isEqualTo("900");
		waitFor("payment link for " + id, 25, () -> {
			var response = stack.call(stack.origin("order").resolve("/api/v1/orders/checkout-sessions/" + id),
					"GET", null, Map.of("Authorization", authorization), 200).path("data");
			return response.path("paymentUrl").isString();
		});
		var payment = UUID.fromString(stack.db("payment",
				"select payment_id from payment_service.payments where checkout_session_id=?", id));
		var replay = stack.call(stack.origin("order").resolve("/api/v1/orders/checkout/pay"), "POST",
				Map.of("quoteToken", quote, "addressId", mock.address),
				Map.of("Authorization", authorization, "Idempotency-Key", key), 200).path("data");
		assertThat(id(replay, "checkoutSessionId")).isEqualTo(id);
		assertThat(replay.path("expiresAt").asString()).isEqualTo(session.path("expiresAt").asString());
		return new Checkout(id, order, product, payment, authorization, session.path("expiresAt").asString());
	}

	private static void approveSeller(Phase2LocalBrokerSupport stack, UUID seller) throws Exception {
		stack.send(stack.identityTopic, seller.toString(), Map.of("eventId", UUID.randomUUID(),
				"eventType", "seller.approved", "occurredAt", Instant.now().toString(), "producer", "identity-service",
				"aggregateId", seller, "version", 1,
				"actor", Map.of("userId", UUID.randomUUID(), "kind", "USER", "context", "ADMIN"),
				"payload", Map.of("sellerId", seller, "userId", seller, "authzVersion", 1, "approvalStatus", "APPROVED")));
		waitFor("Seller wallet", 20, () -> "1".equals(stack.db("finance",
				"select count(*) from finance.wallet where owner_type='SELLER' and owner_id=?", seller)));
	}

	private static UUID capture(Phase2LocalBrokerSupport stack, UUID payment) throws Exception {
		return id(stack.call(stack.provider.resolve("/__fixture/confirm"), "POST", Map.of("paymentId", payment),
				providerHeaders(), 200), "eventId");
	}

	private static void deliverWebhook(Phase2LocalBrokerSupport stack, UUID event) throws Exception {
		URI callback = stack.origin("payment").resolve("/api/v1/payments/webhooks/demo-provider");
		assertThat(stack.call(stack.provider.resolve("/__fixture/send-event"), "POST",
				Map.of("eventId", event, "callback", callback.toString()), providerHeaders(), 200)
				.path("callbackStatus").asInt()).isEqualTo(204);
	}

	private static boolean status(Phase2LocalBrokerSupport stack, Checkout session, String expected) throws Exception {
		return expected.equals(stack.db("order", "select status from orders.checkout_session where checkout_session_id=?",
				session.checkout()));
	}

	private static String event(Phase2LocalBrokerSupport stack, String topic, UUID key, String type) throws Exception {
		try (var observer = stack.observer(topic)) {
			return waitFor(type, 18, () -> {
				for (var record : observer.poll(Duration.ofMillis(500))) {
					if (key.toString().equals(record.key()) && type.equals(JSON.readTree(record.value()).path("eventType").asString())) {
						return record.value();
					}
				}
				return null;
			});
		}
	}

	private static UUID id(JsonNode object, String name) {
		return UUID.fromString(object.path(name).asString());
	}

	private record Checkout(UUID checkout, UUID order, UUID product, UUID payment, String authorization, String expiresAt) {}

	private static final class SaleFixture implements AutoCloseable {
		private final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		private final RSAKey signingKey = new RSAKeyGenerator(2048).keyID("phase2-4a").generate();
		private final java.util.Set<UUID> products = java.util.concurrent.ConcurrentHashMap.newKeySet();
		private final java.util.Set<UUID> customers = java.util.concurrent.ConcurrentHashMap.newKeySet();
		private final List<String> observedCaptures = new java.util.concurrent.CopyOnWriteArrayList<>();
		private final AtomicReference<Boolean> evidence = new AtomicReference<>(Boolean.TRUE);
		private final UUID seller = UUID.randomUUID();
		private final UUID address = UUID.randomUUID();
		private final java.util.concurrent.ExecutorService requests = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

		SaleFixture() throws Exception {
			server.setExecutor(requests);
			server.createContext("/", this::serve);
			server.start();
		}

		URI origin() {
			return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
		}

		void register(UUID product, UUID customer) {
			products.add(product);
			customers.add(customer);
		}

		String authorization(UUID customer) throws Exception {
			var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
					new JWTClaimsSet.Builder().subject(customer.toString()).issuer("ecommerce-identity")
					.audience("ecommerce-api").expirationTime(Date.from(Instant.now().plusSeconds(300)))
					.claim("sessionId", UUID.randomUUID().toString()).claim("authzVersion", 1).build());
			jwt.sign(new RSASSASigner(signingKey));
			return "Bearer " + jwt.serialize();
		}

		private void serve(HttpExchange exchange) throws java.io.IOException {
			try (exchange) {
				String path = exchange.getRequestURI().getPath();
				String token = exchange.getRequestHeaders().getFirst("X-Service-Token");
				if (path.startsWith("/internal/") && !(path.startsWith("/internal/v1/products/")
						? ORDER_TO_PRODUCT : ORDER_TO_IDENTITY).equals(token)) {
					write(exchange, 401, Map.of());
					return;
				}
				if (path.equals("/.well-known/jwks.json")) {
					write(exchange, 200, new JWKSet(signingKey.toPublicJWK()).toJSONObject());
					return;
				}
				if (path.endsWith("/sale-evidence")) {
					saleEvidence(exchange, path.startsWith("/internal/v1/products/"));
					return;
				}
				if (path.equals("/internal/v1/products/batch")) {
					var items = new ArrayList<Map<String, Object>>();
					for (JsonNode product : JSON.readTree(exchange.getRequestBody()).path("productIds")) {
						UUID id = UUID.fromString(product.asString());
						if (products.contains(id)) items.add(Map.of("productId", id, "sellerId", seller,
								"productName", "Phase 2 test product", "priceVnd", 1000,
								"coverImageUrl", "https://example.invalid/4a.png", "sellable", true, "media", List.of()));
					}
					writeData(exchange, items);
					return;
				}
				if (path.endsWith("/authorization-context")) {
					String bearer = exchange.getRequestHeaders().getFirst("Authorization");
					UUID user = UUID.fromString(SignedJWT.parse(bearer.substring(7)).getJWTClaimsSet().getSubject());
					if (!customers.contains(user)) {
						write(exchange, 401, Map.of());
						return;
					}
					writeData(exchange, Map.of("userId", user, "accountType", "CUSTOMER", "accountStatus", "ACTIVE",
							"userStatus", "ACTIVE", "accessMode", "FULL", "authzVersion", 1, "roles", List.of("CUSTOMER"),
							"permissions", List.of("checkout:create:own", "order:read:own"), "sessionActive", true,
							"customerProfile", Map.of("status", "ACTIVE")));
				} else if (path.endsWith("/checkout-eligibility")) {
					writeData(exchange, Map.of("userId", path.split("/")[5], "userStatus", "ACTIVE", "customerStatus", "ACTIVE",
							"paymentBlocked", false, "hasPhone", true, "addressCount", 1, "canCheckout", true, "authzVersion", 1));
				} else if (path.endsWith("/touch")) {
					writeData(exchange, Map.of("applied", true));
				} else if (path.equals("/api/v1/identity/customers/me/addresses")) {
					writeData(exchange, List.of(address()));
				} else if (path.endsWith("/addresses/" + address)) {
					writeData(exchange, address());
				} else if (path.equals("/api/v1/identity/sellers/" + seller)) {
					writeData(exchange, Map.of("sellerId", seller, "sellerName", "Phase 2 test seller"));
				} else if (path.equals("/internal/v1/identity/sellers/status")) {
					writeData(exchange, List.of(Map.of("sellerId", seller, "sellable", true)));
				} else {
					write(exchange, 404, Map.of());
				}
			} catch (java.text.ParseException invalid) {
				throw new java.io.IOException(invalid);
			}
		}

		private Map<String, Object> address() {
			return Map.of("addressId", address, "recipientName", "Fixture customer", "recipientPhone", "0900000000",
					"provinceCode", "01", "provinceName", "Test province", "wardCode", "001", "wardName", "Test ward",
					"addressDetail", "Test address", "createdAt", "2026-01-01T00:00:00Z", "isDefault", true);
		}

		private void saleEvidence(HttpExchange exchange, boolean product) throws java.io.IOException {
			Boolean available = evidence.get();
			if (available == null) {
				write(exchange, 503, Map.of());
				return;
			}
			var body = JSON.readTree(exchange.getRequestBody());
			String completedAt = body.path("completedAt").asString();
			Instant.parse(completedAt);
			String decision = available ? "ELIGIBLE" : "UNKNOWN";
			var items = new ArrayList<Map<String, Object>>();
			for (JsonNode requested : body.path(product ? "productIds" : "sellerIds")) {
				UUID id = UUID.fromString(requested.asString());
				if (product && products.contains(id)) items.add(Map.of("productId", id, "sellerId", seller, "decision", decision));
				if (!product && seller.equals(id)) items.add(Map.of("sellerId", seller, "decision", decision));
			}
			observedCaptures.add(completedAt);
			writeData(exchange, Map.of("completedAt", completedAt, "items", items));
		}

		private static void writeData(HttpExchange exchange, Object data) throws java.io.IOException {
			write(exchange, 200, Map.of("data", data));
		}

		private static void write(HttpExchange exchange, int status, Object data) throws java.io.IOException {
			byte[] bytes = JSON.writeValueAsBytes(data);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
		}

		@Override
		public void close() {
			server.stop(0);
			requests.close();
		}
	}
}
