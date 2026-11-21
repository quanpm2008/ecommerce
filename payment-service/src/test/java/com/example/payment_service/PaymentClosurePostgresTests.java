package com.example.payment_service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.example.payment_service.entity.Payment;
import org.junit.jupiter.api.Test;

class PaymentClosurePostgresTests extends PaymentOutcomePostgresFixture {
    @Test
    void kafkaClosureBeforePaymentPersistsMarkerAndPreventsCreation() throws Exception {
        UUID checkout = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        UUID invalidEvent = UUID.randomUUID();
        publishClose(UUID.randomUUID(), checkout, customer, UUID.randomUUID(), "EXPIRED", "order-service", 1);
        publishClose(checkout, checkout, customer, UUID.randomUUID(), "EXPIRED", "untrusted", 1);
        publishClose(checkout, checkout, customer, UUID.randomUUID(), "EXPIRED", "order-service", 0);
        publishClose(checkout, checkout, customer, invalidEvent, "FAILED", "order-service", 1);
        UUID barrier = UUID.randomUUID();
        publishClose(barrier, barrier, customer, UUID.randomUUID(), "EXPIRED", "order-service", 1);
        // Invalid events are retained separately without changing the collection state.
        await(() -> work.closedAt(barrier) != null);
        assertThat(work.closedAt(checkout)).isNull();
        await(() -> jdbc.sql("SELECT count(*) FROM payment_checkout_event_review WHERE raw_envelope LIKE ?")
                .param("%" + invalidEvent + "%").query(Long.class).single() == 1);
        publishClose(checkout, checkout, customer, UUID.randomUUID(), "EXPIRED", "order-service", 1);
        await(() -> work.closedAt(checkout) != null);
        assertThat(collection.createOrGet(checkout)).isNull();
        assertThat(payments.findByCheckoutSessionId(checkout)).isEmpty();
    }

    @Test
    void closureThenSuccessStopsCollectionAndCreatesOneDurableFullRefund() throws Exception {
        var payment = preparePayment().payment();
        openAtProvider(payment);
        UUID result = provider.completePayment(payment.getPaymentId());
        UUID closure = UUID.randomUUID();
        close(payment, closure, "EXPIRED");
        await(() -> work.closedAt(payment.getCheckoutSessionId()) != null);
        assertThat(work.find(payment.getPaymentId()).orElseThrow().nextRun()).isNull();
        assertThat(collection.read(payment.getCheckoutSessionId()).paymentUrl()).isNull();
        clock.set(START.plusSeconds(15));
        job.runOnce();
        assertThat(payments.findById(payment.getPaymentId()).orElseThrow().isPending()).isTrue();
        assertThat(sendFixture(result)).isEqualTo(204);
        assertThat(sendFixture(result)).isEqualTo(204);
        close(payment, closure, "EXPIRED");
        verifyRefund(payment);
        assertThat(outboxCount(payment)).isZero();
    }

    @Test
    void closureWhileProviderCreateIsInFlightDiscardsStaleLink() throws Exception {
        var payment = preparePayment().payment();
        provider.nextPaymentFault(ProviderMockFixture.Fault.DELAY_AFTER_COMMIT);
        int before = provider.paymentCount();
        clock.set(START.plusSeconds(5));
        var dispatch = CompletableFuture.runAsync(() -> collection.process(payment.getCheckoutSessionId()));
        await(() -> provider.paymentCount() == before + 1);
        close(payment, UUID.randomUUID(), "EXPIRED");
        await(() -> work.closedAt(payment.getCheckoutSessionId()) != null);
        assertThat(dispatch.isDone()).isFalse();
        dispatch.get(8, TimeUnit.SECONDS);
        var closed = payments.findById(payment.getPaymentId()).orElseThrow();
        assertThat(closed.getProviderTransactionId()).isNull();
        assertThat(closed.getPaymentUrl()).isNull();
        assertThat(work.find(payment.getPaymentId()).orElseThrow().nextRun()).isNull();
    }

    @Test
    void authorizedQueryAlreadyInFlightRecordsCaptureAfterCloseWithoutReopeningCollection() throws Exception {
        var payment = preparePayment().payment();
        openAtProvider(payment);
        provider.completePayment(payment.getPaymentId());
        var reached = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        provider.gateNextPaymentQuery(() -> {
            reached.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        clock.set(START.plusSeconds(15));
        var dispatch = CompletableFuture.runAsync(() -> collection.process(payment.getCheckoutSessionId()));
        try {
            assertThat(reached.await(5, TimeUnit.SECONDS)).isTrue();
            close(payment, UUID.randomUUID(), "EXPIRED");
            await(() -> work.closedAt(payment.getCheckoutSessionId()) != null);
        } finally {
            release.countDown();
        }
        dispatch.get(5, TimeUnit.SECONDS);
        verifyRefund(payment);
        assertThat(collection.read(payment.getCheckoutSessionId()).paymentUrl()).isNull();
        assertThat(work.find(payment.getPaymentId()).orElseThrow().nextRun()).isNull();
        assertThat(outboxCount(payment)).isZero();
    }

    @Test
    void successfulPaymentFollowedByClosureStillCreatesOnlyOneRefund() throws Exception {
        var payment = preparePayment().payment();
        openAtProvider(payment);
        UUID result = provider.completePayment(payment.getPaymentId());
        assertThat(sendFixture(result)).isEqualTo(204);
        assertThat(outboxCount(payment)).isEqualTo(1L);
        close(payment, UUID.randomUUID(), "PAYMENT_FAILED");
        close(payment, UUID.randomUUID(), "PAYMENT_FAILED");
        await(() -> refundCount(payment) == 1);
        assertThat(sendFixture(result)).isEqualTo(204);
        verifyRefund(payment);
        assertThat(outboxCount(payment)).isEqualTo(1L);
    }

    @Test
    void racingWebhookAndKafkaCloseConvergeOnSameRefundObligation() throws Exception {
        var payment = preparePayment().payment();
        openAtProvider(payment);
        UUID result = provider.completePayment(payment.getPaymentId());
        var callback = CompletableFuture.supplyAsync(() -> sendFixture(result));
        close(payment, UUID.randomUUID(), "EXPIRED");
        assertThat(callback.get(10, TimeUnit.SECONDS)).isEqualTo(204);
        await(() -> refundCount(payment) == 1);
        verifyRefund(payment);
    }

    private void close(Payment payment, UUID event, String reason) throws Exception {
        UUID checkout = payment.getCheckoutSessionId();
        publishClose(checkout, checkout, payment.getCustomerId(), event, reason, "order-service", 1);
    }

    private long refundCount(Payment payment) {
        return jdbc.sql("SELECT count(*) FROM refunds WHERE payment_id = ? AND scope = 'FULL_CHECKOUT'")
                .param(payment.getPaymentId()).query(Long.class).single();
    }

    private void verifyRefund(Payment payment) {
        assertThat(payments.findById(payment.getPaymentId()).orElseThrow().isSuccess()).isTrue();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM payment_refund_work w JOIN refunds r USING (refund_id)
                WHERE r.payment_id = ? AND r.scope = 'FULL_CHECKOUT' AND r.order_id IS NULL
                  AND r.amount_vnd = 55000 AND r.status = 'PENDING'
                """).param(payment.getPaymentId()).query(Long.class).single()).isEqualTo(1);
        assertThat(refundCount(payment)).isEqualTo(1);
    }
}
