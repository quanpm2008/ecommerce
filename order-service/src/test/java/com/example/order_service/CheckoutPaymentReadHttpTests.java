package com.example.order_service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutPaymentReadHttpTests extends PayHttpTestSupport {
	private static final String PATH = "/api/v1/orders/checkout-sessions/";
	private static final String LINK = "https://provider.example.test/pay/mock";

	@Test
	void pollsAndReplaysTheSameCommittedCheckoutWhenTheLinkBecomesReady() throws Exception {
		var sample = sample(8, 3);
		UUID key = UUID.randomUUID();
		String quote = quote(sample);
		var original = data(pay(sample.customer(), key, quote, sample.address()), 202);
		UUID checkout = UUID.fromString(original.path("checkoutSessionId").asString());
		Instant deadline = Instant.parse(original.path("expiresAt").asString());
		assertThat(paymentReadCalls.get()).isZero();
		assertThat(read(sample.customer(), "current").path("paymentStatus").asString()).isEqualTo("NONE");
		payment(checkout, "PENDING", deadline, false);

		var current = read(sample.customer(), "current");
		assertThat(current.path("checkoutSessionId").asString()).isEqualTo(checkout.toString());
		assertThat(current.path("paymentUrl").asString()).isEqualTo(LINK);
		assertThat(current.path("allowedActions").get(0).asString()).isEqualTo("OPEN_PAYMENT_LINK");
		assertThat(read(sample.customer(), checkout.toString())).isEqualTo(current);
		var replay = pay(sample.customer(), key, quote, sample.address());
		assertThat(data(replay, 200)).isEqualTo(current);
		assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
		assertThat(current.path("expiresAt")).isEqualTo(original.path("expiresAt"));
		assertThat(jdbc.queryForObject("select reserved_quantity from orders.inventory where product_id = ?",
				Integer.class, sample.product())).isEqualTo(3);
		assertThat(jdbc.queryForObject("select count(*) from orders.checkout_outbox where checkout_session_id = ?",
				Integer.class, checkout)).isEqualTo(2);
	}

	@Test
	void enforcesOwnerPermissionAndDistinctPaymentServiceCredentialBeforeLookup() throws Exception {
		var sample = sample(8, 1);
		assertThat(read(sample.customer(), "current").isNull()).isTrue();
		var original = data(pay(sample.customer(), UUID.randomUUID(), quote(sample), sample.address()), 202);
		String id = original.path("checkoutSessionId").asString();
		assertRejected(get(PATH + id, UUID.randomUUID(), null), 404, "NOT_FOUND");
		assertRejected(get(PATH + UUID.randomUUID(), sample.customer(), null), 404, "NOT_FOUND");
		assertRejected(get(PATH + id, null, null), 401, "UNAUTHENTICATED");
		noReadPermission.set(sample.customer());
		assertRejected(get(PATH + id, sample.customer(), null), 403, "FORBIDDEN");
		noReadPermission.set(null);
		inactiveCustomer.set(sample.customer());
		assertRejected(get(PATH + id, sample.customer(), null), 403, "FORBIDDEN");

		String payable = "/internal/v1/orders/checkout-sessions/" + id + "/payable";
		assertRejected(get(payable, sample.customer(), null), 401, "UNAUTHENTICATED");
		assertRejected(get(payable, null, "test-product"), 403, "FORBIDDEN");
		var snapshot = data(get(payable, null, "test-payment-to-order"), 200);
		assertThat(snapshot.path("customerId").asString()).isEqualTo(sample.customer().toString());
		assertThat(snapshot.path("totalAmountVnd")).isEqualTo(original.path("totalAmountVnd"));
		assertThat(snapshot.path("expiresAt")).isEqualTo(original.path("expiresAt"));
		assertThat(snapshot.path("orders").get(0).path("orderId"))
				.isEqualTo(original.path("orders").get(0).path("orderId"));
		assertThat(paymentReadCalls.get()).isZero();
	}

	@Test
	void lookupFailureUses503AndRetainsSessionWhileAbsentPaymentUsesNone() throws Exception {
		var sample = sample(8, 1);
		UUID key = UUID.randomUUID();
		String quote = quote(sample);
		UUID id = UUID.fromString(data(pay(sample.customer(), key, quote, sample.address()), 202).path("checkoutSessionId").asString());
		assertThat(read(sample.customer(), id.toString()).path("paymentStatus").asString()).isEqualTo("NONE");
		for (int status : new int[] {401, 404, 503}) {
			PAYMENT_READS.put(id, new PaymentReply(status, Map.of()));
			assertRejected(get(PATH + id, sample.customer(), null), 503, "SERVICE_UNAVAILABLE");
		}
		assertRejected(pay(sample.customer(), key, quote, sample.address()), 503, "SERVICE_UNAVAILABLE");
		PAYMENT_READS.remove(id);
		assertThat(read(sample.customer(), "current").path("checkoutSessionId").asString()).isEqualTo(id.toString());
		assertThat(jdbc.queryForObject("select reserved_quantity from orders.inventory where product_id = ?",
				Integer.class, sample.product())).isEqualTo(1);
	}

	@Test
	void suppressesLinkAfterEitherDeadlinePaymentCloseOrOrderTerminalStatus() throws Exception {
		var sample = sample(8, 1);
		UUID key = UUID.randomUUID();
		String quote = quote(sample);
		var original = data(pay(sample.customer(), key, quote, sample.address()), 202);
		UUID id = UUID.fromString(original.path("checkoutSessionId").asString());
		Instant deadline = Instant.parse(original.path("expiresAt").asString());
		payment(id, "PENDING", deadline.minusSeconds(901), false);
		assertNoLink(read(sample.customer(), id.toString()), "AWAITING_PAYMENT", "PENDING");
		payment(id, "PENDING", deadline, true);
		assertNoLink(read(sample.customer(), id.toString()), "AWAITING_PAYMENT", "PENDING");
		payment(id, "SUCCESS", deadline, false);
		assertNoLink(read(sample.customer(), id.toString()), "AWAITING_PAYMENT", "SUCCESS");
		payment(id, "PENDING", deadline, false);
		jdbc.update("""
				update orders.checkout_session set created_at = statement_timestamp()-interval '15 minutes',
					expires_at = statement_timestamp() where checkout_session_id = ?
				""", id);
		assertNoLink(read(sample.customer(), id.toString()), "AWAITING_PAYMENT", "PENDING");
		assertNoLink(data(pay(sample.customer(), key, quote, sample.address()), 202), "AWAITING_PAYMENT", "NONE");
		jdbc.update("update orders.checkout_session set status = 'PAID' where checkout_session_id = ?", id);
		payment(id, "SUCCESS", deadline, false);
		assertNoLink(data(pay(sample.customer(), key, quote, sample.address()), 200), "PAID", "SUCCESS");
		jdbc.update("update orders.checkout_session set status = 'CLOSED', close_reason = 'EXPIRED' where checkout_session_id = ?", id);
		assertNoLink(data(pay(sample.customer(), key, quote, sample.address()), 200), "CLOSED", "SUCCESS");
		assertThat(read(sample.customer(), "current").isNull()).isTrue();
	}

	private static void assertNoLink(JsonNode data, String orderStatus, String paymentStatus) {
		assertThat(data.path("status").asString()).isEqualTo(orderStatus);
		assertThat(data.path("paymentStatus").asString()).isEqualTo(paymentStatus);
		assertThat(data.path("paymentUrl").isNull()).isTrue();
		assertThat(data.path("allowedActions").isEmpty()).isTrue();
	}

	private static void payment(UUID checkout, String status, Instant deadline, boolean closed) {
		var view = JSON.createObjectNode().put("checkoutSessionId", checkout.toString())
				.put("paymentId", UUID.randomUUID().toString()).put("status", status)
				.put("paymentUrl", LINK).put("expiresAt", deadline.toString()).put("isSimulated", true);
		if (closed) {
			view.put("collectionClosedAt", deadline.toString());
		}
		PAYMENT_READS.put(checkout, new PaymentReply(200, Map.of("data", view)));
	}

	private JsonNode read(UUID customer, String path) throws Exception {
		return data(get(PATH + path, customer, null), 200);
	}

	private static JsonNode data(HttpResponse<String> response, int status) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		return body(response).path("data");
	}

	private HttpResponse<String> get(String path, UUID customer, String serviceToken) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
		if (customer != null) {
			request.header("Authorization", "Bearer " + token(customer));
		}
		if (serviceToken != null) {
			request.header("X-Service-Token", serviceToken);
		}
		try (var client = HttpClient.newHttpClient()) {
			return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
	}
}
