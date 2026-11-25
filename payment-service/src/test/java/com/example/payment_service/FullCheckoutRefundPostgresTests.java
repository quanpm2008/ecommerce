package com.example.payment_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.example.payment_service.client.ProviderContracts.REFUND_ATTEMPT_NOT_FOUND;

import java.time.Instant;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.example.payment_service.entity.Payment;
import com.example.payment_service.entity.Refund;
import com.example.payment_service.entity.RefundAttempt;
import com.example.payment_service.entity.RefundScope;
import com.example.payment_service.entity.RefundReason;
import com.example.payment_service.entity.RefundStatus;
import com.example.payment_service.client.ProviderContracts.CreateRefundRequest;
import com.example.payment_service.client.ProviderHttpGateway.ProviderHttpException;
import com.example.payment_service.repository.RefundAttemptRepository;
import com.example.payment_service.repository.RefundRepository;
import com.example.payment_service.service.FullCheckoutRefundService;
import com.example.payment_service.service.RefundReconciliationJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"payment.refund.query-interval=60s", "payment.internal.order-token=phase3-refund-lookup"})
class FullCheckoutRefundPostgresTests extends PaymentOutcomePostgresFixture {
    @Autowired FullCheckoutRefundService refundService;
    @Autowired RefundRepository refunds;
    @Autowired RefundAttemptRepository attempts;

    @Test
    void orderCredentialCanReadRefundObligationsByOrderOrClosedCheckout() throws Exception {
        var full = fundedRefund(true);
        Payment otherPayment = preparePayment().payment();
        UUID orderId = UUID.randomUUID();
        var orderRefund = refunds.saveAndFlush(Refund.createOrderRefund(otherPayment.getPaymentId(), orderId, 1000L,
                RefundReason.CUSTOMER_CANCEL, "order:" + orderId));

        var wrong = refundLookup("orderIds=" + orderId, "wrong");
        assertThat(wrong.statusCode()).isEqualTo(401);
        var response = refundLookup("orderIds=" + orderId, "phase3-refund-lookup");
        assertThat(response.statusCode()).isEqualTo(200);
        var orderEntries = json.readTree(response.body()).path("data");
        assertThat(orderEntries.size()).isOne();
        assertThat(orderEntries.get(0).path("refundId").asString()).isEqualTo(orderRefund.getRefundId().toString());
        assertThat(orderEntries.get(0).path("scope").asString()).isEqualTo("ORDER");
        var refundForCheckout = refundLookup("checkoutSessionIds=" + full.payment().getCheckoutSessionId(), "phase3-refund-lookup");
        assertThat(refundForCheckout.statusCode()).isEqualTo(200);
        var checkoutEntries = json.readTree(refundForCheckout.body()).path("data");
        assertThat(checkoutEntries.size()).isOne();
        assertThat(checkoutEntries.get(0).path("refundId").asString()).isEqualTo(full.refund().getRefundId().toString());
        assertThat(checkoutEntries.get(0).path("orderId").isNull()).isTrue();
    }

    private HttpResponse<String> refundLookup(String query, String credential) throws Exception {
        var uri = URI.create("http://127.0.0.1:" + port + "/internal/v1/payments/refunds?" + query);
        var request = HttpRequest.newBuilder(uri).header("X-Service-Token", credential).GET().build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void sharedProviderWorkerRefundsTwoExistingOrderObligationsOnOnePayment() throws Exception {
        var payment = preparePayment().payment();
        openAtProvider(payment);
        assertThat(sendFixture(provider.completePayment(payment.getPaymentId()))).isEqualTo(204);
        UUID firstOrder = UUID.randomUUID(), secondOrder = UUID.randomUUID();
        var first = refunds.saveAndFlush(Refund.createOrderRefund(payment.getPaymentId(), firstOrder, 20_000L,
                RefundReason.CUSTOMER_CANCEL, "order:" + firstOrder));
        var second = refunds.saveAndFlush(Refund.createOrderRefund(payment.getPaymentId(), secondOrder, 35_000L,
                RefundReason.SELLER_REJECT, "order:" + secondOrder));
        try (var consumer = resultConsumer()) {
            for (var refund : List.of(first, second)) {
                UUID refundId = refund.getRefundId();
                jdbc.sql("INSERT INTO payment_refund_work(refund_id, next_run_at) VALUES (?, ?)")
                        .params(refundId, java.sql.Timestamp.from(START)).update();
                refundService.process(refundId);
                refundService.process(refundId);
                var payload = awaitEvent(consumer, refundId, "refund.succeeded", 1).path("payload");
                assertThat(payload.path("orderId").asString()).isEqualTo(refund.getOrderId().toString());
                assertThat(payload.path("amountVnd").asLong()).isEqualTo(refund.getAmountVnd());
                assertThat(payload.path("scope").asString()).isEqualTo("ORDER");
                assertThat(provider.refundPostCount(refundId)).isOne();
                assertThat(attemptsFor(refundId)).hasSize(1);
            }
        }
    }

    @Test
    void lostResponseAfterProviderCommitQueriesSameAttemptAndPaysOnlyOnce() throws Exception {
        var prepared = fundedRefund(true);
        UUID refundId = prepared.refund().getRefundId();
        provider.nextRefundFault(ProviderMockFixture.Fault.DROP_AFTER_COMMIT);
        refundService.process(refundId);

        assertThat(refunds.findById(refundId).orElseThrow().getStatus()).isEqualTo(RefundStatus.PENDING);
        var attemptBeforeRecovery = attemptsFor(refundId);
        assertThat(attemptBeforeRecovery).hasSize(1);
        assertThat(provider.refundPostCount(refundId)).isEqualTo(1);
        assertThat(provider.refundQueryCount(refundId)).isZero();
        assertThat(provider.refundSuccessCount(refundId)).isEqualTo(1);
        assertThat(eventCount(refundId, "refund.succeeded")).isZero();
        assertThat(nextRun(refundId)).isEqualTo(START.plusSeconds(60));

        clock.set(START.plusSeconds(59));
        refundService.process(refundId);
        assertThat(provider.refundQueryCount(refundId)).isZero();
        clock.set(START.plusSeconds(60));
        try (var reader = resultConsumer()) {
            new RefundReconciliationJob(refundService).runOnce();
            var result = awaitEvent(reader, refundId, "refund.succeeded", 1).path("payload");
            assertThat(result.get("orderId").isNull()).isTrue();
            assertThat(result.path("scope").asString()).isEqualTo("FULL_CHECKOUT");
            assertThat(result.path("paymentId").asString()).isEqualTo(prepared.payment().getPaymentId().toString());
            assertThat(result.path("amountVnd").asLong()).isEqualTo(55000L);
        }
        assertThat(attemptsFor(refundId)).isEqualTo(attemptBeforeRecovery);
        assertThat(provider.refundPostCount(refundId)).isEqualTo(1);
        assertThat(provider.refundQueryCount(refundId)).isEqualTo(1);
        assertThat(queryHistory(attemptBeforeRecovery.getFirst())).containsExactly("SUCCESS");
        assertSuccess(prepared);
    }

    @Test
    void verifiedFailuresRetryAfterOneFiveFifteenMinutesThenRequireCustomer() throws Exception {
        var prepared = fundedRefund(false);
        UUID refundId = prepared.refund().getRefundId();
        long[] timeline = {0, 60, 360, 1260};
        try (var reader = resultConsumer()) {
            for (int retry = 0; retry < timeline.length; retry++) {
                long elapsed = timeline[retry];
                if (retry > 0) {
                    clock.set(START.plusSeconds(elapsed - 1));
                    refundService.process(refundId);
                    assertThat(attemptsFor(refundId)).hasSize(retry);
                }
                clock.set(START.plusSeconds(elapsed));
                provider.nextRefundFault(ProviderMockFixture.Fault.REJECT);
                refundService.process(refundId);
                assertThat(attemptsFor(refundId)).hasSize(retry + 1);
                var state = refunds.findById(refundId).orElseThrow();
                assertThat(state.getStatus()).isEqualTo(RefundStatus.FAILED);
                assertThat(state.getRetryCycle()).isZero();
                assertThat(state.getRetryCount()).isEqualTo(retry);
                assertThat(state.isCustomerRetryRequired()).isEqualTo(retry == 3);
                assertThat(state.getIdempotencyKey()).isEqualTo(prepared.refund().getIdempotencyKey());
                var payload = awaitEvent(reader, refundId, "refund.failed", retry + 1).path("payload");
                assertThat(payload.path("retryCount").asInt()).isEqualTo(retry);
                assertThat(payload.path("retryCycle").asInt()).isZero();
                assertThat(payload.path("retryExhausted").asBoolean()).isEqualTo(retry == 3);
                assertThat(payload.path("customerId").asString()).isEqualTo(prepared.payment().getCustomerId().toString());
                assertThat(payload.path("checkoutSessionId").asString())
                        .isEqualTo(prepared.payment().getCheckoutSessionId().toString());
                assertThat(payload.path("orderId").isNull()).isTrue();
            }
        }
        clock.set(START.plusSeconds(3600));
        refundService.process(refundId);
        assertThat(provider.refundPostCount(refundId)).isEqualTo(4);
        assertThat(provider.refundQueryCount(refundId)).isEqualTo(3);
        assertThat(provider.refundSuccessCount(refundId)).isZero();
        assertThat(eventCount(refundId, "refund.failed")).isEqualTo(4);
        assertThat(attemptsFor(refundId).subList(0, 3)).allSatisfy(attempt -> assertThat(queryHistory(attempt)).containsExactly("FAILED"));
        UUID persistedFailure = provider.refundEvent(refundId, attemptsFor(refundId).getLast());
        assertThat(sendFixture(persistedFailure)).isEqualTo(204);
        assertThat(inboxCount(persistedFailure)).isEqualTo(1);
        assertThat(eventCount(refundId, "refund.failed")).isEqualTo(4);
        assertThat(jdbc.sql("SELECT next_run_at IS NULL FROM payment_refund_work WHERE refund_id = ?")
                .param(refundId).query(Boolean.class).single()).isTrue();
    }

    @Test
    void aConfirmedFailureCanRetryOnNewAttemptAndSucceedWithSameRefund() throws Exception {
        var prepared = fundedRefund(false);
        UUID refundId = prepared.refund().getRefundId();
        provider.nextRefundFault(ProviderMockFixture.Fault.REJECT);
        refundService.process(refundId);
        clock.set(START.plusSeconds(60));
        try (var reader = resultConsumer()) {
            refundService.process(refundId);
            awaitEvent(reader, refundId, "refund.failed", 1);
            awaitEvent(reader, refundId, "refund.succeeded", 2);
        }

        assertThat(attemptsFor(refundId)).hasSize(2);
        assertThat(provider.refundPostCount(refundId)).isEqualTo(2);
        assertThat(provider.refundQueryCount(refundId)).isEqualTo(1);
        assertThat(refunds.findById(refundId).orElseThrow().getRetryCount()).isEqualTo(1);
        assertSuccess(prepared);
    }

    @Test
    void callbackBeforePostResponseAndConflictingOrRepeatedDeliveriesDoNotRepeatRefund() throws Exception {
        var prepared = fundedRefund(true);
        UUID refundId = prepared.refund().getRefundId();
        provider.useInstantCallback(callback());
        provider.nextRefundFault(ProviderMockFixture.Fault.CALLBACK_BEFORE_RESPONSE);
        try {
            refundService.process(refundId);
        } finally {
            provider.useInstantCallback(null);
        }
        var attempt = attempts.findById(attemptsFor(refundId).getFirst()).orElseThrow();
        UUID eventId = provider.refundEvent(refundId, attempt.getAttemptId());
        assertThat(inboxCount(eventId)).isEqualTo(1);
        assertThat(sendFixture(eventId)).isEqualTo(204);
        assertThat(sendSigned(refundResult(prepared, attempt.getAttemptId(), eventId, attempt.getProviderRefundId(),
                "SUCCESS", 55000, "VND"), clock.instant())).isEqualTo(409);
        UUID repeat = UUID.randomUUID();
        assertThat(sendSigned(refundResult(prepared, attempt.getAttemptId(), repeat, attempt.getProviderRefundId(),
                "SUCCESS", 55000, "VND"), clock.instant())).isEqualTo(204);
        assertThat(inboxCount(repeat)).isEqualTo(1);

        UUID conflict = UUID.randomUUID();
        assertThat(sendSigned(refundResult(prepared, attempt.getAttemptId(), conflict, attempt.getProviderRefundId(),
                "FAILED", 55000, "VND"), clock.instant())).isEqualTo(409);
        assertThat(sendSigned(refundResult(prepared, attempt.getAttemptId(), conflict, attempt.getProviderRefundId(),
                "SUCCESS", 55001, "VND"), clock.instant())).isEqualTo(409);
        assertThat(sendSigned(refundResult(prepared, attempt.getAttemptId(), conflict, attempt.getProviderRefundId(),
                "SUCCESS", 55000, "USD"), clock.instant())).isEqualTo(409);
        assertThat(sendSigned(refundResult(prepared, attempt.getAttemptId(), conflict, "different-ref",
                "SUCCESS", 55000, "VND"), clock.instant())).isEqualTo(409);
        assertThat(sendSigned(refundResult(prepared, UUID.randomUUID(), conflict, attempt.getProviderRefundId(),
                "SUCCESS", 55000, "VND"), clock.instant())).isEqualTo(409);
        assertThat(inboxCount(conflict)).isZero();
        assertThat(provider.refundPostCount(refundId)).isEqualTo(1);
        assertThat(provider.refundQueryCount(refundId)).isZero();
        assertSuccess(prepared);
    }

    @Test
    void concurrentWorkerAndWebhookDuringPostConvergeOnOneReceipt() throws Exception {
        var prepared = fundedRefund(true);
        UUID refundId = prepared.refund().getRefundId();
        provider.nextRefundFault(ProviderMockFixture.Fault.DELAY_AFTER_COMMIT);
        var dispatched = CompletableFuture.runAsync(() -> refundService.process(refundId));
        await(() -> provider.refundSuccessCount(refundId) == 1);

        refundService.process(refundId);
        assertThat(provider.refundPostCount(refundId)).isEqualTo(1);
        assertThat(provider.refundQueryCount(refundId)).isZero();
        UUID attempt = attemptsFor(refundId).getFirst();
        assertThat(sendFixture(provider.refundEvent(refundId, attempt))).isEqualTo(204);
        dispatched.get(10, TimeUnit.SECONDS);
        assertThat(attemptsFor(refundId)).containsExactly(attempt);
        assertSuccess(prepared);
    }

    @Test
    void callbackWhileJ5QueriesSameAttemptPreventsSecondEffect() throws Exception {
        var prepared = fundedRefund(false);
        UUID refundId = prepared.refund().getRefundId();
        provider.nextRefundFault(ProviderMockFixture.Fault.DROP_AFTER_COMMIT);
        refundService.process(refundId);
        UUID attemptId = attemptsFor(refundId).getFirst();
        UUID eventId = provider.refundEvent(refundId, attemptId);
        clock.set(START.plusSeconds(60));

        var release = new CountDownLatch(1);
        provider.holdNextRefundQuery(release);
        var query = CompletableFuture.runAsync(() -> refundService.process(refundId));
        try {
            await(() -> provider.refundQueryCount(refundId) == 1);
            assertThat(query.isDone()).isFalse();
            assertThat(sendFixture(eventId)).isEqualTo(204);
            assertThat(inboxCount(eventId)).isEqualTo(1);
        } finally {
            release.countDown();
        }
        query.get(10, TimeUnit.SECONDS);
        assertThat(attemptsFor(refundId)).containsExactly(attemptId);
        assertThat(queryHistory(attemptId)).containsExactly("SUCCESS");
        assertThat(provider.refundPostCount(refundId)).isEqualTo(1);
        assertSuccess(prepared);
    }

    @Test
    void unconfirmedDispatchWithProvider404StaysQueryableAndDoesNotPostAgain() throws Exception {
        var prepared = fundedRefund(true);
        UUID refundId = prepared.refund().getRefundId();
        var attempt = attempts.saveAndFlush(new RefundAttempt(prepared.refund(), clock.instant()));
        provider.nextRefundQueryReturnsGeneric404();
        refundService.process(refundId);
        assertThat(attemptsFor(refundId)).containsExactly(attempt.getAttemptId());
        assertThat(provider.refundPostCount(refundId)).isZero();
        assertThat(provider.refundQueryCount(refundId)).isEqualTo(1);
        assertThat(queryHistory(attempt.getAttemptId())).containsExactly("UNKNOWN");
        assertThat(refunds.findById(refundId).orElseThrow().getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(nextRun(refundId)).isEqualTo(START.plusSeconds(60));
        assertThat(eventCount(refundId, "refund.failed")).isZero();
    }

    @Test
    void definitive404ClosesOldAttemptAndRetriesOnceOnSchedule() throws Exception {
        var prepared = fundedRefund(true);
        UUID refundId = prepared.refund().getRefundId();
        var old = attempts.saveAndFlush(new RefundAttempt(prepared.refund(), clock.instant()));
        refundService.process(refundId);

        assertThat(refunds.findById(refundId).orElseThrow().getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(attempts.findById(old.getAttemptId()).orElseThrow().getErrorCode()).isEqualTo(REFUND_ATTEMPT_NOT_FOUND);
        assertThat(eventCount(refundId, "refund.failed")).isOne();
        assertThat(provider.refundPostCount(refundId)).isZero();
        assertThat(nextRun(refundId)).isEqualTo(START.plusSeconds(60));

        var delayed = new CreateRefundRequest(refundId, old.getAttemptId(), prepared.payment().getPaymentId(),
                prepared.payment().getProviderTransactionId(), prepared.refund().getAmountVnd(), "VND");
        assertThatThrownBy(() -> gateway.createRefund(delayed)).isInstanceOfSatisfying(ProviderHttpException.class,
                rejected -> assertThat(rejected.statusCode()).isEqualTo(409));
        assertThat(provider.refundSuccessCount(refundId)).isZero();

        clock.set(START.plusSeconds(59));
        refundService.process(refundId);
        assertThat(attemptsFor(refundId)).containsExactly(old.getAttemptId());
        clock.set(START.plusSeconds(60));
        refundService.process(refundId);
        assertThat(attemptsFor(refundId)).hasSize(2).startsWith(old.getAttemptId());
        assertThat(queryHistory(old.getAttemptId())).containsExactly("FAILED", "FAILED");
        assertThat(provider.refundPostCount(refundId)).isEqualTo(2);
        assertSuccess(prepared);
    }

    @Test
    void signedSuccessForFailedAttemptPausesRefundWithoutSecondPost() throws Exception {
        var prepared = fundedRefund(false);
        UUID refundId = prepared.refund().getRefundId();
        provider.nextRefundFault(ProviderMockFixture.Fault.REJECT);
        refundService.process(refundId);
        var first = attempts.findById(attemptsFor(refundId).getFirst()).orElseThrow();
        UUID eventId = UUID.randomUUID();
        byte[] contradiction = refundResult(prepared, first.getAttemptId(), eventId, first.getProviderRefundId(),
                "SUCCESS", 55000, "VND");
        var releaseQuery = new CountDownLatch(1);
        provider.holdNextRefundQuery(releaseQuery);
        clock.set(START.plusSeconds(60));
        var running = CompletableFuture.runAsync(() -> refundService.process(refundId));
        try {
            await(() -> provider.refundQueryCount(refundId) == 1);
            assertThat(sendSigned(contradiction, clock.instant())).isEqualTo(204);
        } finally {
            releaseQuery.countDown();
        }
        running.get(10, TimeUnit.SECONDS);
        assertThat(sendSigned(contradiction, clock.instant())).isEqualTo(204);
        assertThat(inboxCount(eventId)).isOne();
        assertThat(queryHistory(first.getAttemptId())).containsExactlyInAnyOrder("CONFLICT", "FAILED");
        assertThat(jdbc.sql("SELECT review_required_at IS NOT NULL FROM payment_refund_work WHERE refund_id = ?")
                .param(refundId).query(Boolean.class).single()).isTrue();

        clock.set(START.plusSeconds(1260));
        assertThat(refundService.dueRefunds()).doesNotContain(refundId);
        refundService.process(refundId);
        assertThat(attemptsFor(refundId)).containsExactly(first.getAttemptId());
        assertThat(provider.refundPostCount(refundId)).isOne();
        assertThat(eventCount(refundId, "refund.succeeded")).isZero();
        assertThat(refunds.findById(refundId).orElseThrow().getStatus()).isEqualTo(RefundStatus.FAILED);
    }

    @Test
    void refundWebhookRollbackKeepsInboxAttemptAndOutboxRetryable() throws Exception {
        var prepared = fundedRefund(false);
        UUID refundId = prepared.refund().getRefundId();
        provider.nextRefundFault(ProviderMockFixture.Fault.DROP_AFTER_COMMIT);
        refundService.process(refundId);
        UUID attemptId = attemptsFor(refundId).getFirst();
        UUID eventId = provider.refundEvent(refundId, attemptId);
        String constraint = "ck_test_refund_" + refundId.toString().replace("-", "");
        jdbc.sql("ALTER TABLE outbox_events ADD CONSTRAINT " + constraint
                + " CHECK (aggregate_id <> '" + refundId + "'::uuid)").update();
        try {
            assertThat(sendFixture(eventId)).isEqualTo(503);
            assertThat(inboxCount(eventId)).isZero();
            assertThat(refunds.findById(refundId).orElseThrow().getStatus()).isEqualTo(RefundStatus.PENDING);
            assertThat(attempts.findById(attemptId).orElseThrow().isPending()).isTrue();
            assertThat(eventCount(refundId, "refund.succeeded")).isZero();
        } finally {
            jdbc.sql("ALTER TABLE outbox_events DROP CONSTRAINT " + constraint).update();
        }
        assertThat(sendFixture(eventId)).isEqualTo(204);
        assertThat(sendFixture(eventId)).isEqualTo(204);
        assertThat(inboxCount(eventId)).isEqualTo(1);
        assertSuccess(prepared);
    }

    private FundedRefund fundedRefund(boolean closeFirst) throws Exception {
        var payment = preparePayment().payment();
        openAtProvider(payment);
        UUID success = provider.completePayment(payment.getPaymentId());
        UUID checkout = payment.getCheckoutSessionId();
        if (!closeFirst) {
            assertThat(sendFixture(success)).isEqualTo(204);
        }
        publishClose(checkout, checkout, payment.getCustomerId(), UUID.randomUUID(), "EXPIRED", "order-service", 1);
        await(() -> work.closedAt(checkout) != null);
        if (closeFirst) {
            assertThat(sendFixture(success)).isEqualTo(204);
        }
        var refund = refunds.findByPaymentIdAndScope(payment.getPaymentId(), RefundScope.FULL_CHECKOUT).orElseThrow();
        return new FundedRefund(payments.findById(payment.getPaymentId()).orElseThrow(), refund);
    }

    private byte[] refundResult(FundedRefund source, UUID attempt, UUID event, String providerRefundId,
            String status, long amount, String currency) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event);
        body.put("type", "refund.result");
        body.put("occurredAt", clock.instant().toString());
        body.put("paymentId", source.payment().getPaymentId());
        body.put("providerTransactionId", source.payment().getProviderTransactionId());
        body.put("amountVnd", amount);
        body.put("currency", currency);
        body.put("status", status);
        body.put("completedAt", clock.instant().toString());
        body.put("refundId", source.refund().getRefundId());
        body.put("attemptId", attempt);
        body.put("providerRefundId", providerRefundId);
        if ("FAILED".equals(status)) {
            body.put("errorCode", "REFUND_DECLINED");
        }
        return json.writeValueAsBytes(body);
    }

    private void assertSuccess(FundedRefund source) {
        UUID refundId = source.refund().getRefundId();
        var result = refunds.findById(refundId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(result.getOrderId()).isNull();
        assertThat(result.isCustomerRetryRequired()).isFalse();
        assertThat(result.getAmountVnd()).isEqualTo(source.payment().getAmountVnd());
        assertThat(result.getIdempotencyKey()).isEqualTo(source.refund().getIdempotencyKey());
        assertThat(refunds.findByPaymentId(source.payment().getPaymentId())).singleElement()
                .satisfies(existing -> assertThat(existing.getRefundId()).isEqualTo(refundId));
        assertThat(provider.refundSuccessCount(refundId)).isEqualTo(1);
        assertThat(eventCount(refundId, "refund.succeeded")).isEqualTo(1);
    }

    private List<UUID> attemptsFor(UUID refundId) {
        return jdbc.sql("""
                SELECT attempt_id FROM payment_refund_attempts WHERE refund_id = ? ORDER BY retry_cycle, attempt_number
                """).param(refundId).query(UUID.class).list();
    }

    private long eventCount(UUID refundId, String type) {
        return jdbc.sql("SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = ?")
                .params(refundId, type).query(Long.class).single();
    }

    private Instant nextRun(UUID refundId) {
        return jdbc.sql("SELECT next_run_at FROM payment_refund_work WHERE refund_id = ?")
                .param(refundId).query((rs, row) -> rs.getTimestamp(1).toInstant()).single();
    }

    private List<String> queryHistory(UUID attemptId) {
        return jdbc.sql("SELECT outcome FROM payment_refund_attempt_queries WHERE attempt_id = ? ORDER BY checked_at")
                .param(attemptId).query(String.class).list();
    }

    private record FundedRefund(Payment payment, Refund refund) { }
}
