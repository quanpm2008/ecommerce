package com.example.order_service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutConcurrencyPostgresTests extends PayHttpTestSupport {
	@Test
	void twoBuyersCannotReserveTheLastUnitTwice() throws Exception {
		var first = sample(1, 1);
		var second = new Sample(UUID.randomUUID(), first.product(), UUID.randomUUID(), 1);
		ADDRESSES.put(second.address(), second.customer());
		String firstQuote = quote(first);
		String secondQuote = quote(second);
		var results = race(() -> pay(first.customer(), UUID.randomUUID(), firstQuote, first.address()),
				() -> pay(second.customer(), UUID.randomUUID(), secondQuote, second.address()));
		assertThat(results).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(202, 409);
		assertRejected(results.stream().filter(response -> response.statusCode() == 409).findFirst().orElseThrow(),
				409, "CHECKOUT_ITEMS_INVALID");
		assertThat(count("select count(*) from orders.checkout_session where customer_id in (?, ?)",
				first.customer(), second.customer())).isEqualTo(1);
		assertThat(count("select reserved_quantity from orders.inventory where product_id = ?", first.product())).isEqualTo(1);
		assertThat(count("select count(*) from orders.reservation where product_id = ?", first.product())).isEqualTo(1);
	}

	@Test
	void sellerUpdateAndPaySerializeAgainstTheSameInventoryVersion() throws Exception {
		var sample = sample(1, 1);
		String quote = quote(sample);
		var results = race(() -> pay(sample.customer(), UUID.randomUUID(), quote, sample.address()),
				() -> adjustStock(sample.product(), 0, 0));
		boolean held = results.getFirst().statusCode() == 202;
		if (held) {
			assertRejected(results.getLast(), 409, "VERSION_CONFLICT");
		} else {
			assertRejected(results.getFirst(), 409, "CHECKOUT_ITEMS_INVALID");
			assertThat(results.getLast().statusCode()).as(results.getLast().body()).isEqualTo(200);
		}
		var row = jdbc.queryForMap("select on_hand_quantity, reserved_quantity, row_version from orders.inventory where product_id = ?",
				sample.product());
		assertThat(row).containsEntry("on_hand_quantity", held ? 1 : 0)
				.containsEntry("reserved_quantity", held ? 1 : 0).containsEntry("row_version", 1L);
		assertThat(count("select count(*) from orders.stock_movement where product_id = ?", sample.product()))
				.isEqualTo(held ? 0 : 1);
	}

	@Test
	void concurrentSameKeyAndConcurrentNewAliasEachKeepOneCheckout() throws Exception {
		assertConcurrentReplay(false);
		assertConcurrentReplay(true);
	}

	@Test
	void distinctIntentsCompeteForOnePendingSlotAndDatabaseRejectsDuplicateKeysAndIntents() throws Exception {
		var sample = sample(5, 1);
		var firstKey = UUID.randomUUID();
		var secondKey = UUID.randomUUID();
		String firstQuote = quote(sample);
		String secondQuote = quote(sample);
		var result = race(() -> pay(sample.customer(), firstKey, firstQuote, sample.address()),
				() -> pay(sample.customer(), secondKey, secondQuote, sample.address()));
		assertThat(result).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(202, 409);
		assertRejected(result.stream().filter(response -> response.statusCode() == 409).findFirst().orElseThrow(),
				409, "CHECKOUT_PENDING_EXISTS");
		UUID id = id(result.stream().filter(response -> response.statusCode() == 202).findFirst().orElseThrow());
		assertThat(count("select count(*) from orders.idempotency_record where result_ref = ?", id)).isEqualTo(1);
		assertDuplicateSession(id, "gen_random_uuid()", "gen_random_uuid()::text", "AWAITING_PAYMENT");
		assertDuplicateSession(id, "checkout_intent_id", "gen_random_uuid()::text", "CLOSED");
		assertDuplicateSession(id, "gen_random_uuid()", "idempotency_key", "CLOSED");
		assertThatThrownBy(() -> jdbc.update("""
				insert into orders.idempotency_record select * from orders.idempotency_record where result_ref = ?
				""", id)).isInstanceOf(DuplicateKeyException.class);
	}

	private void assertConcurrentReplay(boolean newAlias) throws Exception {
		var sample = sample(5, 1);
		String token = quote(sample);
		var firstKey = UUID.randomUUID();
		var secondKey = newAlias ? UUID.randomUUID() : firstKey;
		var replies = race(() -> pay(sample.customer(), firstKey, token, sample.address()),
				() -> pay(sample.customer(), secondKey, token, sample.address()));
		assertThat(replies).extracting(HttpResponse::statusCode).containsExactly(202, 202);
		UUID id = id(replies.getFirst());
		assertThat(id(replies.getLast())).isEqualTo(id);
		assertThat(replies.stream().filter(reply -> reply.headers().firstValue("Idempotent-Replayed").isPresent()).count())
				.isEqualTo(1);
		assertThat(count("select count(*) from orders.checkout_session where customer_id = ?", sample.customer())).isEqualTo(1);
		assertThat(count("select count(*) from orders.idempotency_record where result_ref = ?", id)).isEqualTo(newAlias ? 2 : 1);
		assertThat(count("select count(*) from orders.checkout_outbox where checkout_session_id = ?", id)).isEqualTo(2);
		assertThat(count("select reserved_quantity from orders.inventory where product_id = ?", sample.product())).isEqualTo(1);
	}

	private void assertDuplicateSession(UUID id, String intentColumn, String keyColumn, String state) {
		assertThatThrownBy(() -> jdbc.update("""
				insert into orders.checkout_session (checkout_session_id, checkout_intent_id, customer_id, checkout_source,
				idempotency_key, request_hash, total_amount_vnd, currency, status, address_id, created_at, expires_at, close_reason)
				select gen_random_uuid(), %s, customer_id, checkout_source, %s, request_hash, total_amount_vnd,
				currency, ?, address_id, created_at, expires_at, ? from orders.checkout_session where checkout_session_id = ?
				""".formatted(intentColumn, keyColumn), state, "CLOSED".equals(state) ? "EXPIRED" : null, id))
				.isInstanceOf(DuplicateKeyException.class);
	}

	private int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	private static UUID id(HttpResponse<String> response) {
		return UUID.fromString(body(response).path("data").path("checkoutSessionId").asString());
	}

	private static <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
		var barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var left = executor.submit(() -> {
				barrier.await(5, TimeUnit.SECONDS);
				return first.call();
			});
			var right = executor.submit(() -> {
				barrier.await(5, TimeUnit.SECONDS);
				return second.call();
			});
			return List.of(left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS));
		}
	}
}
