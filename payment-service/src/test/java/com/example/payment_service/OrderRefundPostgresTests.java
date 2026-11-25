package com.example.payment_service;

import com.example.payment_service.entity.Payment;
import com.example.payment_service.entity.RefundScope;
import com.example.payment_service.repository.RefundRepository;
import com.example.payment_service.service.FullCheckoutRefundService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "payment.refund.query-interval=60s")
class OrderRefundPostgresTests extends PaymentOutcomePostgresFixture {
    @Autowired RefundRepository refunds;
    @Autowired FullCheckoutRefundService refundWorker;

    @Test
    void cancellationBeforeSnapshotWaitsAndDistinctEventIdsCannotRefundTwice() throws Exception {
        var payment = captured();
        UUID seller = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        UUID early = UUID.randomUUID(), late = UUID.randomUUID(), replay = UUID.randomUUID();
        publishCancel(payment, early, first, seller, 20_000);
        await(() -> "WAITING_DEPENDENCY".equals(state(early)));
        assertThat(refunds.findByPaymentId(payment.getPaymentId())).isEmpty();
        var lines = List.of(line(first, seller, 20_000), line(second, seller, 35_000));
        publishPaid(payment, lines);
        await(() -> "APPLIED".equals(state(early)));
        publishCancel(payment, late, second, seller, 35_000);
        publishCancel(payment, replay, first, seller, 20_000);
        publishCancel(payment, early, first, seller, 20_000);
        publishPaid(payment, lines.reversed());
        await(() -> "APPLIED".equals(state(late)) && "APPLIED".equals(state(replay)));
        try (var reader = resultConsumer()) {
            var obligations = refunds.findByPaymentId(payment.getPaymentId());
            assertThat(obligations).hasSize(2);
            for (var refund : obligations) {
                UUID id = refund.getRefundId();
                refundWorker.process(id);
                refundWorker.process(id);
                var event = awaitEvent(reader, id, "refund.succeeded", 1).path("payload");
                assertThat(event.path("orderId").asString()).isEqualTo(refund.getOrderId().toString());
                assertThat(event.path("amountVnd").asLong()).isEqualTo(refund.getAmountVnd());
                assertThat(provider.refundPostCount(id)).isOne();
            }
        }
    }

    @Test
    void rejectsWrongSellerOrderAmountAndCloseAfterPaid() throws Exception {
        var payment = captured();
        UUID order = UUID.randomUUID(), seller = UUID.randomUUID(), checkout = payment.getCheckoutSessionId();
        publishPaid(payment, List.of(line(order, seller, payment.getAmountVnd())));
        await(() -> snapshot(checkout));
        UUID wrongSeller = UUID.randomUUID(), wrongOrder = UUID.randomUUID(), wrongAmount = UUID.randomUUID();
        publishCancel(payment, wrongSeller, order, UUID.randomUUID(), payment.getAmountVnd());
        publishCancel(payment, wrongOrder, UUID.randomUUID(), seller, payment.getAmountVnd());
        publishCancel(payment, wrongAmount, order, seller, payment.getAmountVnd() - 1);
        await(() -> List.of(wrongSeller, wrongOrder, wrongAmount).stream().allMatch(id -> "FAILED_REVIEW".equals(state(id))));
        UUID close = UUID.randomUUID();
        publishClose(checkout, checkout, payment.getCustomerId(), close, "EXPIRED", "order-service", 2);
        await(() -> jdbc.sql("SELECT count(*) FROM payment_checkout_event_review WHERE raw_envelope LIKE ?")
                .param("%" + close + "%").query(Integer.class).single() == 1);
        assertThat(work.closedAt(checkout)).isNull();
        assertThat(refunds.findByPaymentId(payment.getPaymentId())).isEmpty();
    }

    @Test
    void closeWinsWhenSnapshotHasNotArrivedAndLeavesOnlyFullCheckoutRefund() throws Exception {
        var payment = captured();
        UUID order = UUID.randomUUID(), seller = UUID.randomUUID(), checkout = payment.getCheckoutSessionId();
        UUID cancel = UUID.randomUUID();
        publishCancel(payment, cancel, order, seller, payment.getAmountVnd());
        await(() -> "WAITING_DEPENDENCY".equals(state(cancel)));
        publishClose(checkout, checkout, payment.getCustomerId(), UUID.randomUUID(), "EXPIRED", "order-service", 1);
        await(() -> "FAILED_REVIEW".equals(state(cancel)));
        UUID paid = publishPaid(payment, List.of(line(order, seller, payment.getAmountVnd())));
        await(() -> jdbc.sql("SELECT count(*) FROM payment_checkout_event_review WHERE raw_envelope LIKE ?")
                .param("%" + paid + "%").query(Integer.class).single() == 1);
        assertThat(snapshot(checkout)).isFalse();
        assertThat(refunds.findByPaymentId(payment.getPaymentId())).singleElement()
                .satisfies(refund -> assertThat(refund.getScope()).isEqualTo(RefundScope.FULL_CHECKOUT));
    }

    @Test
    void lostProviderResponseForOrderIsRecoveredByQueryingTheSameAttempt() throws Exception {
        var payment = captured();
        UUID seller = UUID.randomUUID(), order = UUID.randomUUID(), cancellation = UUID.randomUUID();
        publishPaid(payment, List.of(line(order, seller, payment.getAmountVnd())));
        publishCancel(payment, cancellation, order, seller, payment.getAmountVnd());
        await(() -> "APPLIED".equals(state(cancellation)));
        UUID refund = refunds.findByOrderId(order).orElseThrow().getRefundId();
        provider.nextRefundFault(ProviderMockFixture.Fault.DROP_AFTER_COMMIT);
        refundWorker.process(refund);
        UUID attempt = jdbc.sql("SELECT attempt_id FROM payment_refund_attempts WHERE refund_id=?")
                .param(refund).query(UUID.class).single();
        assertThat(provider.refundPostCount(refund)).isOne();
        clock.set(START.plusSeconds(60));
        refundWorker.process(refund);
        assertThat(jdbc.sql("SELECT attempt_id FROM payment_refund_attempts WHERE refund_id=?")
                .param(refund).query(UUID.class).list()).containsExactly(attempt);
        assertThat(provider.refundQueryCount(refund)).isOne();
        assertThat(provider.refundPostCount(refund)).isOne();
        assertThat(refunds.findById(refund).orElseThrow().getStatus())
                .isEqualTo(com.example.payment_service.entity.RefundStatus.SUCCESS);
    }

    private Payment captured() {
        var payment = preparePayment().payment();
        openAtProvider(payment);
        assertThat(sendFixture(provider.completePayment(payment.getPaymentId()))).isEqualTo(204);
        return payment;
    }

    private static Map<String, Object> line(UUID order, UUID seller, long amount) {
        return Map.of("orderId", order, "sellerId", seller, "totalAmountVnd", amount, "commissionBps", 500);
    }

    private UUID publishPaid(Payment p, List<Map<String, Object>> orders) throws Exception {
        UUID event = UUID.randomUUID();
        publish(event, "checkout.paid", p.getCheckoutSessionId(), Map.of(
                "checkoutSessionId", p.getCheckoutSessionId(), "customerId", p.getCustomerId(),
                "paymentId", p.getPaymentId(), "paidAt", START.toString(), "orders", orders,
                "cartItems", List.of(), "source", "BUY_NOW"));
        return event;
    }

    private void publishCancel(Payment p, UUID event, UUID order, UUID seller, long amount) throws Exception {
        publish(event, "order.cancelled", order, Map.of("checkoutSessionId", p.getCheckoutSessionId(),
                "customerId", p.getCustomerId(), "orderId", order, "sellerId", seller, "totalAmountVnd", amount,
                "wasPaid", true, "refundReason", "CUSTOMER_CANCEL", "cancelledBy", "CUSTOMER"));
    }

    private void publish(UUID event, String type, UUID aggregate, Map<String, Object> payload) throws Exception {
        try (var writer = new KafkaProducer<String, String>(Map.of("bootstrap.servers", BOOTSTRAP),
                new StringSerializer(), new StringSerializer())) {
            var envelope = Map.of("eventId", event, "eventType", type, "producer", "order-service",
                    "aggregateId", aggregate, "version", 1, "occurredAt", START.toString(), "payload", payload);
            writer.send(new ProducerRecord<>(CLOSURES, aggregate.toString(), json.writeValueAsString(envelope))).get();
        }
    }

    private String state(UUID event) {
        return jdbc.sql("SELECT status FROM payment_order_refund_inbox WHERE event_id = ?")
                .param(event).query(String.class).optional().orElse(null);
    }

    private boolean snapshot(UUID checkout) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM payment_paid_checkout WHERE checkout_session_id=?)")
                .param(checkout).query(Boolean.class).single();
    }
}
