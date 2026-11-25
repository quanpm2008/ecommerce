package com.example.finance_service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.example.finance_service.PendingSettlementPostgresTests.*;
import static org.assertj.core.api.Assertions.assertThat;

class SettlementLifecyclePostgresTests {
	@BeforeAll
	static void startStack() throws Exception {
		start();
	}

	@AfterAll
	static void stopStack() {
		stop();
	}

	@Test
	void settlesAndRefundsDifferentOrdersAfterRestartWithoutDuplicateLedger() throws Exception {
		UUID seller = UUID.randomUUID(), customer = UUID.randomUUID(), checkout = UUID.randomUUID();
		UUID payment = UUID.randomUUID(), confirmed = UUID.randomUUID(), cancelled = UUID.randomUUID();
		UUID refund = UUID.randomUUID(), confirmationEvent = UUID.randomUUID(), refundEvent = UUID.randomUUID();
		approve(seller);
		long platformBefore = platformAvailable();
		var confirmation = Map.<String, Object>of("orderId", confirmed, "sellerId", seller, "customerId", customer,
				"confirmedAt", Instant.now().toString(), "confirmedBy", "CUSTOMER");
		var refundPayload = Map.<String, Object>of("orderId", cancelled, "paymentId", payment,
				"refundId", refund, "scope", "ORDER", "amountVnd", 10_000);
		long confirmationOffset = publish(ORDER_TOPIC, confirmed,
				wire(confirmationEvent, "order.confirmed", "order-service", confirmed, confirmation));
		long refundOffset = publish(PAYMENT_TOPIC, refund,
				wire(refundEvent, "refund.succeeded", "payment-service", refund, refundPayload));
		await(() -> "WAITING_DEPENDENCY".equals(state(confirmationEvent)) && committed(ORDER_TOPIC, confirmationOffset)
				&& "WAITING_DEPENDENCY".equals(state(refundEvent)) && committed(PAYMENT_TOPIC, refundOffset));

		finance.close();
		startFinance();
		publish(ORDER_TOPIC, checkout, paid(UUID.randomUUID(), checkout, payment,
				List.of(line(confirmed, seller, 20_000, 500), line(cancelled, seller, 10_000, 500))));
		await(() -> "APPLIED".equals(state(confirmationEvent)));
		assertThat(state(refundEvent)).isEqualTo("WAITING_DEPENDENCY");
		assertThat(wallet(seller)).isEqualTo("9500:19000");

		var cancellation = Map.<String, Object>of("orderId", cancelled, "checkoutSessionId", checkout,
				"sellerId", seller, "customerId", customer, "wasPaid", true, "totalAmountVnd", 10_000,
				"cancelledBy", "SELLER", "reasonCode", "OUT_OF_STOCK", "refundReason", "SELLER_REJECT");
		publish(ORDER_TOPIC, cancelled, wire(UUID.randomUUID(), "order.cancelled", "order-service", cancelled, cancellation));
		await(() -> "APPLIED".equals(state(refundEvent)));

		UUID duplicateConfirmation = UUID.randomUUID(), duplicateRefund = UUID.randomUUID();
		publish(ORDER_TOPIC, confirmed, wire(duplicateConfirmation, "order.confirmed", "order-service", confirmed, confirmation));
		publish(PAYMENT_TOPIC, refund, wire(duplicateRefund, "refund.succeeded", "payment-service", refund, refundPayload));
		await(() -> "APPLIED".equals(state(duplicateConfirmation)) && "APPLIED".equals(state(duplicateRefund)));
		UUID conflictingRefund = UUID.randomUUID(), conflictingEvent = UUID.randomUUID();
		var conflict = new java.util.HashMap<>(refundPayload);
		conflict.put("refundId", conflictingRefund);
		publish(PAYMENT_TOPIC, conflictingRefund, wire(conflictingEvent, "refund.succeeded", "payment-service", conflictingRefund, conflict));
		await(() -> "FAILED_REVIEW".equals(state(conflictingEvent)));
		assertThat(wallet(seller)).isEqualTo("0:19000");
		assertThat(platformAvailable()).isEqualTo(platformBefore + 1_000);
		assertThat(orderState(confirmed)).isEqualTo("SETTLED");
		assertThat(orderState(cancelled)).isEqualTo("REFUNDED");
		assertThat(ledgerCount(confirmed, "SETTLEMENT_RELEASED")).isEqualTo(2);
		assertThat(ledgerCount(cancelled, "REFUND_DEBIT")).isEqualTo(2);
		await(() -> db.queryForObject("""
				select count(*) from finance.funds_available_outbox where order_id=? and published_at is not null
				""", Integer.class, confirmed) == 1);
	}

	@Test
	void cancellationKeepsPendingAndWrongPaymentCannotDebitIt() throws Exception {
		UUID seller = UUID.randomUUID(), order = UUID.randomUUID(), checkout = UUID.randomUUID(), payment = UUID.randomUUID();
		UUID cancellation = UUID.randomUUID(), wrong = UUID.randomUUID(), refund = UUID.randomUUID();
		approve(seller);
		publish(ORDER_TOPIC, checkout, paid(UUID.randomUUID(), checkout, payment, List.of(line(order, seller, 100, 500))));
		publish(ORDER_TOPIC, order, wire(cancellation, "order.cancelled", "order-service", order,
				Map.of("orderId", order, "checkoutSessionId", checkout, "sellerId", seller, "customerId", UUID.randomUUID(),
						"wasPaid", true, "totalAmountVnd", 100, "refundReason", "CUSTOMER_CANCEL")));
		await(() -> "APPLIED".equals(state(cancellation)));
		assertThat(wallet(seller)).isEqualTo("95:0");
		publish(PAYMENT_TOPIC, refund, wire(wrong, "refund.succeeded", "payment-service", refund,
				Map.of("orderId", order, "paymentId", UUID.randomUUID(), "refundId", refund, "scope", "ORDER", "amountVnd", 100)));
		await(() -> "FAILED_REVIEW".equals(state(wrong)));
		assertThat(wallet(seller)).isEqualTo("95:0");
		assertThat(orderState(order)).isEqualTo("PENDING_REFUND");
		assertThat(ledgerCount(order, "REFUND_DEBIT")).isZero();
	}

	@Test
	void failedReleaseRollsBackWalletLedgerAndOutboxThenRetries() throws Exception {
		UUID seller = UUID.randomUUID(), order = UUID.randomUUID(), checkout = UUID.randomUUID();
		UUID confirmation = UUID.randomUUID(), sale = UUID.randomUUID();
		approve(seller);
		publish(ORDER_TOPIC, order, wire(confirmation, "order.confirmed", "order-service", order,
				Map.of("orderId", order, "sellerId", seller, "customerId", UUID.randomUUID(),
						"confirmedAt", Instant.now().toString(), "confirmedBy", "SYSTEM")));
		await(() -> "WAITING_DEPENDENCY".equals(state(confirmation)));
		var constraint = "phase3_test_block_release";
		db.execute("alter table finance.funds_available_outbox add constraint " + constraint
				+ " check (order_id <> '" + order + "') not valid");
		try {
			publish(ORDER_TOPIC, checkout, paid(sale, checkout, UUID.randomUUID(), List.of(line(order, seller, 100, 500))));
			await(() -> "APPLIED".equals(state(sale)));
			assertThat(state(confirmation)).isEqualTo("WAITING_DEPENDENCY");
			assertThat(orderState(order)).isEqualTo("PENDING");
			assertThat(wallet(seller)).isEqualTo("95:0");
			assertThat(ledgerCount(order, "SETTLEMENT_RELEASED")).isZero();
		} finally {
			db.execute("alter table finance.funds_available_outbox drop constraint " + constraint);
		}
		await(() -> "APPLIED".equals(state(confirmation)));
		assertThat(wallet(seller)).isEqualTo("0:95");
		assertThat(ledgerCount(order, "SETTLEMENT_RELEASED")).isEqualTo(2);
	}

	private static String wire(UUID event, String type, String producer, UUID aggregate, Map<String, Object> payload) {
		return envelope(event, type, producer, aggregate, payload, Map.of("kind", "SERVICE"));
	}

	private static String wallet(UUID seller) {
		return db.queryForObject("""
				select pending_balance_vnd || ':' || available_balance_vnd from finance.wallet where owner_id=?
				""", String.class, seller);
	}

	private static long platformAvailable() {
		return db.queryForObject("select available_balance_vnd from finance.wallet where owner_type='PLATFORM'", Long.class);
	}

	private static String orderState(UUID order) {
		return db.queryForObject("select status from finance.settlement where order_id=?", String.class, order);
	}

	private static int ledgerCount(UUID order, String type) {
		return db.queryForObject("""
				select count(*) from finance.wallet_transaction w join finance.settlement s using (settlement_id)
				where s.order_id=? and w.transaction_type=?
				""", Integer.class, order, type);
	}
}
