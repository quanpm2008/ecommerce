package com.example.order_service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutReplayPostgresTests extends PayHttpTestSupport {
	@Test
	void replaysAfterQuoteTtlAndRegistersEquivalentIntentAliasWithoutNewEffects() throws Exception {
		var sample = sample(5, 2);
		var key = UUID.randomUUID();
		var alias = UUID.randomUUID();
		String original = quote(sample);
		var first = data(pay(sample.customer(), key, original, sample.address()), 202);
		var id = id(first);
		CLOCK.advance(Duration.ofMinutes(5));
		checkoutSourceCalls.set(0);
		lastTouch.set(null);
		int originalAuthCalls = authCalls.get();
		var expiredQuoteResponse = replay(pay(sample.customer(), key, original, sample.address()), 202);
		String equivalent = renewFixtureQuote(original);
		assertThat(equivalent).isNotEqualTo(original);
		assertThat(replay(pay(sample.customer(), key, equivalent, sample.address()), 202)).isEqualTo(first);
		assertThat(replay(pay(sample.customer(), alias, equivalent, sample.address()), 202)).isEqualTo(first);
		assertThat(expiredQuoteResponse).isEqualTo(first);
		assertThat(authCalls.get()).isEqualTo(originalAuthCalls + 3);
		assertThat(checkoutSourceCalls.get()).isZero();
		assertThat(lastTouch.get()).isNull();
		assertThat(jdbc.queryForList("select idempotency_key from orders.idempotency_record where result_ref = ?",
				String.class, id)).containsExactlyInAnyOrder(key.toString(), alias.toString());
		assertThat(jdbc.queryForObject("select count(*) from orders.checkout_outbox where checkout_session_id = ?",
				Integer.class, id)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from orders.reservation where product_id = ?",
				Integer.class, sample.product())).isEqualTo(1);
	}

	@Test
	void rejectsBothTypesOfChangedPayloadAndPreservesOriginalMapping() throws Exception {
		var sample = sample(5, 2);
		var key = UUID.randomUUID();
		String quote = quote(sample);
		var first = data(pay(sample.customer(), key, quote, sample.address()), 202);
		var otherAddress = UUID.randomUUID();
		assertRejected(pay(sample.customer(), key, quote, otherAddress), 409, "IDEMPOTENCY_KEY_REUSED");
		assertRejected(pay(sample.customer(), UUID.randomUUID(), quote, otherAddress), 409, "CHECKOUT_INTENT_CONFLICT");
		assertThat(replay(pay(sample.customer(), key, quote, sample.address()), 202)).isEqualTo(first);
		assertThat(jdbc.queryForObject("select count(*) from orders.idempotency_record where result_ref = ?",
				Integer.class, id(first))).isEqualTo(1);
	}

	@Test
	void overduePendingReturnsNoLinkAndClosedReplayUsesSnapshotAfterAddressDeletion() throws Exception {
		var sample = sample(5, 2);
		var key = UUID.randomUUID();
		String quote = quote(sample);
		var first = data(pay(sample.customer(), key, quote, sample.address()), 202);
		var id = id(first);
		jdbc.update("""
				update orders.checkout_session set created_at = statement_timestamp() - interval '15 minutes',
				expires_at = statement_timestamp() where checkout_session_id = ?
				""", id);
		var overdue = replay(pay(sample.customer(), key, quote, sample.address()), 202);
		assertThat(overdue.path("status").asString()).isEqualTo("AWAITING_PAYMENT");
		assertThat(overdue.path("paymentUrl").isNull()).isTrue();
		close(id, sample);
		ADDRESSES.remove(sample.address());
		PRICES.remove(sample.product());
		financeStatus.set(503);
		lastTouch.set(null);
		checkoutSourceCalls.set(0);
		var closed = replay(pay(sample.customer(), key, quote, sample.address()), 200);
		assertThat(id(closed)).isEqualTo(id);
		assertThat(closed.path("expiresAt")).isEqualTo(overdue.path("expiresAt"));
		assertThat(closed.path("status").asString()).isEqualTo("CLOSED");
		assertThat(closed.path("closeReason").asString()).isEqualTo("EXPIRED");
		assertThat(closed.path("orders").get(0).path("status").asString()).isEqualTo("EXPIRED");
		assertThat(closed.path("paymentUrl").isNull()).isTrue();
		assertThat(closed.path("shippingAddress")).isEqualTo(first.path("shippingAddress"));
		assertThat(checkoutSourceCalls.get()).isZero();
		assertThat(lastTouch.get()).isNull();
		assertThat(jdbc.queryForObject("select reserved_quantity from orders.inventory where product_id = ?",
				Integer.class, sample.product())).isZero();
		assertRejected(pay(sample.customer(), UUID.randomUUID(), quote(sample), sample.address()), 404, "NOT_FOUND");
	}

	@Test
	void deniesForeignQuoteAndRevokedOwnerBeforeReturningReplay() throws Exception {
		var sample = sample(5, 2);
		var key = UUID.randomUUID();
		String quote = quote(sample);
		data(pay(sample.customer(), key, quote, sample.address()), 202);
		assertRejected(pay(UUID.randomUUID(), key, quote, sample.address()), 400, "CHECKOUT_QUOTE_INVALID");
		revokedCustomer.set(sample.customer());
		assertRejected(pay(sample.customer(), key, quote, sample.address()), 401, "UNAUTHENTICATED");
	}

	@Test
	void acceptsNewIntentAfterClosureAndRejectsKeyThatPointsAtOtherCheckout() throws Exception {
		var sample = sample(5, 2);
		var firstKey = UUID.randomUUID();
		String firstQuote = quote(sample);
		var firstId = id(data(pay(sample.customer(), firstKey, firstQuote, sample.address()), 202));
		close(firstId, sample);
		var secondKey = UUID.randomUUID();
		String secondQuote = quote(sample);
		var secondId = id(data(pay(sample.customer(), secondKey, secondQuote, sample.address()), 202));
		assertThat(secondId).isNotEqualTo(firstId);
		assertRejected(pay(sample.customer(), firstKey, secondQuote, sample.address()), 409, "CHECKOUT_INTENT_CONFLICT");
		assertThat(id(replay(pay(sample.customer(), firstKey, firstQuote, sample.address()), 200))).isEqualTo(firstId);
		assertThat(id(replay(pay(sample.customer(), secondKey, secondQuote, sample.address()), 202))).isEqualTo(secondId);
	}

	private void close(UUID session, Sample sample) {
		jdbc.update("update orders.checkout_session set status = 'CLOSED', close_reason = 'EXPIRED' where checkout_session_id = ?", session);
		jdbc.update("update orders.purchase_order set status = 'EXPIRED' where checkout_session_id = ?", session);
		jdbc.update("update orders.reservation set status = 'RELEASED', resolved_at = now() where product_id = ?", sample.product());
		jdbc.update("update orders.inventory set reserved_quantity = 0 where product_id = ?", sample.product());
	}

	private static JsonNode replay(HttpResponse<String> response, int status) {
		assertThat(response.headers().firstValue("Idempotent-Replayed")).contains("true");
		return data(response, status);
	}

	private static JsonNode data(HttpResponse<String> response, int status) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		return body(response).path("data");
	}

	private static UUID id(JsonNode data) {
		return UUID.fromString(data.path("checkoutSessionId").asString());
	}

	private static String renewFixtureQuote(String original) throws Exception {
		var payload = (ObjectNode) JSON.readTree(Base64.getUrlDecoder().decode(original.split("\\.")[0]));
		Instant now = CLOCK.instant();
		payload.set("issuedAt", JSON.valueToTree(now));
		payload.set("expiresAt", JSON.valueToTree(now.plus(Duration.ofMinutes(5))));
		var encoder = Base64.getUrlEncoder().withoutPadding();
		String encoded = encoder.encodeToString(JSON.writeValueAsBytes(payload));
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
		return encoded + "." + encoder.encodeToString(mac.doFinal(encoded.getBytes(StandardCharsets.US_ASCII)));
	}
}
