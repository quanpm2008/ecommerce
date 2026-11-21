package com.example.payment_service.service;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.example.payment_service.client.GatewayStatus;
import com.example.payment_service.client.ProviderContracts.PaymentQueryResponse;
import com.example.payment_service.entity.OutboxEvent;
import com.example.payment_service.entity.Payment;
import com.example.payment_service.entity.Refund;
import com.example.payment_service.entity.RefundScope;
import com.example.payment_service.entity.RefundStatus;
import com.example.payment_service.repository.OutboxEventRepository;
import com.example.payment_service.repository.PaymentCheckoutGuardRepository;
import com.example.payment_service.repository.PaymentCollectionWorkRepository;
import com.example.payment_service.repository.PaymentRepository;
import com.example.payment_service.repository.RefundRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentOutcomeService {
    private static final String PROVIDER = "DEMO_PROVIDER";
    private final EntityManager entities;
    private final PaymentRepository payments;
    private final PaymentCollectionWorkRepository work;
    private final PaymentCheckoutGuardRepository closures;
    private final RefundRepository refunds;
    private final OutboxEventRepository outbox;
    private final Clock clock;

    public PaymentOutcomeService(EntityManager entities, PaymentRepository payments, PaymentCollectionWorkRepository work,
            PaymentCheckoutGuardRepository closures, RefundRepository refunds, OutboxEventRepository outbox, Clock clock) {
        this.entities = entities;
        this.payments = payments;
        this.work = work;
        this.closures = closures;
        this.refunds = refunds;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void paymentWebhook(ProviderResult result, byte[] digest) {
        if (!recordWebhook(result.eventId(), digest)) {
            return;
        }
        UUID checkoutId = payments.findCheckoutIdByPaymentId(result.paymentId()).orElseThrow(OutcomeConflict::new);
        var guard = work.lockCheckout(checkoutId);
        Payment payment = payments.findByIdForUpdate(result.paymentId()).orElseThrow(OutcomeConflict::new);
        applyTerminal(payment, guard.isClosed(), result, clock.instant());
    }

    @Transactional
    public boolean recordWebhook(String eventId, byte[] digest) {
        int inserted = entities.createNativeQuery("""
                INSERT INTO provider_webhook_inbox(provider, event_id, payload_digest, received_at)
                VALUES (:provider, :eventId, :digest, :now) ON CONFLICT (provider, event_id) DO NOTHING
                """).setParameter("provider", PROVIDER).setParameter("eventId", eventId)
                .setParameter("digest", digest).setParameter("now", clock.instant()).executeUpdate();
        if (inserted == 1) {
            return true;
        }
        byte[] known = (byte[]) entities.createNativeQuery("""
                SELECT payload_digest FROM provider_webhook_inbox WHERE provider = :provider AND event_id = :eventId
                """).setParameter("provider", PROVIDER).setParameter("eventId", eventId).getSingleResult();
        if (!MessageDigest.isEqual(known, digest)) {
            throw new OutcomeConflict();
        }
        return false;
    }

    @Transactional
    public void checkoutClosed(UUID eventId, UUID checkoutId, UUID customerId, long version, Instant closedAt) {
        work.lockCheckout(checkoutId);
        Payment payment = payments.findByCheckoutSessionId(checkoutId).orElse(null);
        if (payment != null && !payment.getCustomerId().equals(customerId)) {
            throw new OutcomeConflict();
        }
        Instant now = clock.instant();
        if (closures.acceptClose(eventId, checkoutId, version, closedAt, now) && payment != null) {
            payment.setPaymentUrl(null);
            work.stop(payment.getPaymentId());
            if (payment.isSuccess()) {
                scheduleFullRefund(payment, now);
            }
        }
    }

    // Collection invokes this in the same transaction and under the checkout lock used for recording the observation.
    void applyObservation(Payment payment, boolean closed, PaymentQueryResponse observation, Instant now) {
        var result = new ProviderResult(null, payment.getPaymentId(), observation.providerTransactionId(),
                observation.amountVnd(), observation.currency(), observation.status(), observation.completedAt(),
                observation.errorCode());
        applyTerminal(payment, closed, result, now);
        entities.createNativeQuery("""
                UPDATE payment_collection_observation SET applied_at = :now
                WHERE payment_id = :paymentId AND applied_at IS NULL
                """).setParameter("now", now).setParameter("paymentId", payment.getPaymentId()).executeUpdate();
    }

    private void applyTerminal(Payment payment, boolean closed, ProviderResult result, Instant now) {
        if (!PROVIDER.equals(payment.getProvider()) || !payment.getCurrency().equals(result.currency())
                || payment.getAmountVnd() != result.amountVnd()
                || (payment.getProviderTransactionId() != null
                    && !payment.getProviderTransactionId().equals(result.providerTransactionId()))
                || (payment.getProviderTransactionId() == null
                    && payments.existsByProviderTransactionIdAndPaymentIdNot(result.providerTransactionId(), payment.getPaymentId()))
                || (result.status() == GatewayStatus.SUCCESS && !result.completedAt().isBefore(payment.getExpiresAt()))) {
            throw new OutcomeConflict();
        }
        if (payment.isSuccess()) {
            return;
        }
        if (payment.isFailed()) {
            if (result.status() == GatewayStatus.SUCCESS) {
                throw new OutcomeConflict();
            }
            return;
        }
        payment.setPaymentUrl(null);
        work.stop(payment.getPaymentId());
        UUID paymentId = payment.getPaymentId();
        UUID checkoutId = payment.getCheckoutSessionId();
        if (result.status() == GatewayStatus.SUCCESS) {
            payment.markSuccess(result.providerTransactionId(), result.completedAt());
            if (closed) {
                scheduleFullRefund(payment, now);
            } else {
                outbox.save(new OutboxEvent(null, "payment.succeeded", paymentId, Map.of(
                        "paymentId", paymentId, "checkoutSessionId", checkoutId,
                        "amountVnd", result.amountVnd(), "completedAt", result.completedAt().toString())));
            }
        } else {
            payment.markFailed(result.errorCode(), result.providerTransactionId(), result.completedAt());
            if (!closed) {
                outbox.save(new OutboxEvent(null, "payment.failed", paymentId, Map.of(
                        "paymentId", paymentId, "checkoutSessionId", checkoutId, "errorCode", result.errorCode())));
            }
        }
    }

    private void scheduleFullRefund(Payment payment, Instant now) {
        String key = "full-checkout:" + payment.getPaymentId();
        Refund refund = refunds.findByPaymentIdAndScope(payment.getPaymentId(), RefundScope.FULL_CHECKOUT)
                .orElseGet(() -> refunds.saveAndFlush(
                Refund.createFullCheckoutRefund(payment.getPaymentId(), payment.getAmountVnd(), key)));
        if (refund.getStatus() != RefundStatus.PENDING) {
            return;
        }
        entities.createNativeQuery("""
                INSERT INTO payment_refund_work(refund_id, next_run_at)
                VALUES (:refundId, :now) ON CONFLICT (refund_id) DO NOTHING
                """).setParameter("refundId", refund.getRefundId()).setParameter("now", now).executeUpdate();
    }

    public record ProviderResult(String eventId, UUID paymentId, String providerTransactionId,
            long amountVnd, String currency, GatewayStatus status, Instant completedAt, String errorCode) { }

    public static class OutcomeConflict extends RuntimeException { }
}
