package com.example.payment_service.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.example.payment_service.client.GatewayStatus;
import com.example.payment_service.client.PaymentGateway;
import com.example.payment_service.client.ProviderContracts.CreateRefundRequest;
import com.example.payment_service.client.ProviderContracts.RefundResponse;
import com.example.payment_service.client.ProviderHttpGateway.ProviderHttpException;
import com.example.payment_service.client.ProviderHttpGateway.RefundAttemptNotFound;
import com.example.payment_service.client.ProviderHttpGateway.UncertainResultException;
import com.example.payment_service.controller.ProviderWebhookController.RefundReferences;
import com.example.payment_service.controller.ProviderWebhookController.RefundWebhookHandler;
import com.example.payment_service.entity.OutboxEvent;
import com.example.payment_service.entity.Payment;
import com.example.payment_service.entity.Refund;
import com.example.payment_service.entity.RefundAttempt;
import com.example.payment_service.entity.RefundScope;
import com.example.payment_service.entity.RefundStatus;
import com.example.payment_service.repository.OutboxEventRepository;
import com.example.payment_service.repository.PaymentRepository;
import com.example.payment_service.repository.RefundAttemptRepository;
import com.example.payment_service.repository.RefundRepository;
import com.example.payment_service.repository.RefundWorkRepository;
import com.example.payment_service.service.PaymentOutcomeService.OutcomeConflict;
import com.example.payment_service.service.PaymentOutcomeService.ProviderResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FullCheckoutRefundService implements RefundWebhookHandler {
    private static final List<Duration> RETRY_DELAYS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(15));
    private final RefundRepository refunds;
    private final RefundAttemptRepository attempts;
    private final RefundWorkRepository work;
    private final PaymentRepository payments;
    private final OutboxEventRepository outbox;
    private final PaymentOutcomeService outcomes;
    private final ObjectProvider<PaymentGateway> provider;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Duration queryInterval;
    private final Duration leaseLength;

    public FullCheckoutRefundService(RefundRepository refunds, RefundAttemptRepository attempts,
            RefundWorkRepository work, PaymentRepository payments, OutboxEventRepository outbox,
            PaymentOutcomeService outcomes, ObjectProvider<PaymentGateway> provider,
            PlatformTransactionManager manager, Clock clock,
            @Value("${payment.refund.query-interval:}") Duration queryInterval,
            @Value("${payment.provider.timeout:2s}") Duration providerTimeout) {
        this.refunds = refunds;
        this.attempts = attempts;
        this.work = work;
        this.payments = payments;
        this.outbox = outbox;
        this.outcomes = outcomes;
        this.provider = provider;
        this.transactions = new TransactionTemplate(manager);
        this.clock = clock;
        if (queryInterval != null && !queryInterval.isPositive()) {
            throw new IllegalArgumentException("Refund query interval must be positive");
        }
        this.queryInterval = queryInterval;
        this.leaseLength = providerTimeout.multipliedBy(4).plusSeconds(2);
    }

    public List<UUID> dueRefunds() {
        return queryInterval == null || provider.getIfAvailable() == null ? List.of() : work.due(clock.instant());
    }

    public void process(UUID refundId) {
        PaymentGateway gateway = provider.getIfAvailable();
        if (queryInterval == null || gateway == null) {
            return;
        }
        Dispatch dispatch = transactions.execute(tx -> claim(refundId));
        if (dispatch == null || work.isHeldForReview(refundId)) {
            return;
        }
        var response = send(gateway, dispatch);
        Dispatch retry = transactions.execute(tx -> finish(dispatch, response));
        if (retry != null && !work.isHeldForReview(refundId)) {
            var retried = send(gateway, retry);
            transactions.executeWithoutResult(tx -> finish(retry, retried));
        }
    }

    private Dispatch claim(UUID refundId) {
        Refund refund = refunds.findByIdForUpdate(refundId).orElse(null);
        if (refund == null || refund.getStatus() == RefundStatus.SUCCESS
                || refund.isCustomerRetryRequired()) {
            return null;
        }
        Instant now = clock.instant();
        UUID lease = UUID.randomUUID();
        if (!work.claim(refundId, lease, now, now.plus(leaseLength))) {
            return null;
        }
        Payment payment = paymentFor(refund);
        var latest = attempts.findFirstByRefundIdOrderByRetryCycleDescAttemptNumberDesc(refundId).orElse(null);
        if (latest == null) {
            RefundAttempt initial = attempts.saveAndFlush(new RefundAttempt(refund, now));
            return dispatch(refund, payment, initial, lease, Action.POST);
        }
        Action action = latest.isPending() ? Action.QUERY : Action.VERIFY_FAILURE;
        return dispatch(refund, payment, latest, lease, action);
    }

    private Dispatch finish(Dispatch dispatch, RefundResponse response) {
        UUID refundId = dispatch.command().refundId();
        Refund refund = refunds.findByIdForUpdate(refundId).orElseThrow();
        RefundAttempt attempt = attempts.findByAttemptIdAndRefundId(dispatch.command().attemptId(), refundId).orElseThrow();
        Instant now = clock.instant();
        if (contradictsFailure(refund, attempt, response)) {
            work.holdForReview(refundId, attempt.getAttemptId(), now);
            return null;
        }
        boolean matches = response != null && matches(refund, attempt, response);
        if (dispatch.action() != Action.POST) {
            String outcome = response == null ? "UNKNOWN" : matches ? response.status().name() : "CONFLICT";
            work.logQuery(attempt.getAttemptId(), now, outcome);
        }
        if (!work.ownsLease(refundId, dispatch.lease())) {
            // Callback or another worker already owns the state transition.
            return null;
        }
        if (!matches) {
            work.release(refundId, now.plus(queryInterval));
            return null;
        }
        if (dispatch.action() == Action.VERIFY_FAILURE) {
            // Query the previous attempt again before authorizing another provider POST.
            if (attempt.getRetryCycle() == refund.getRetryCycle()) {
                refund.retry();
            }
            var next = attempts.saveAndFlush(new RefundAttempt(refund, now));
            return dispatch(refund, paymentFor(refund), next, dispatch.lease(), Action.POST);
        }
        apply(refund, paymentFor(refund), attempt, response, now);
        return null;
    }

    @Override
    @Transactional
    public void apply(ProviderResult common, RefundReferences references, byte[] digest) {
        if (!outcomes.recordWebhook(common.eventId(), digest)) {
            return;
        }
        Refund refund = refunds.findByIdForUpdate(references.refundId()).orElseThrow(OutcomeConflict::new);
        var attempt = attempts.findByAttemptIdAndRefundId(references.attemptId(), refund.getRefundId())
                .orElseThrow(OutcomeConflict::new);
        Payment payment = paymentFor(refund);
        if (!payment.getPaymentId().equals(common.paymentId())
                || !payment.getProviderTransactionId().equals(common.providerTransactionId())) {
            throw new OutcomeConflict();
        }
        var response = new RefundResponse(references.providerRefundId(), common.status(), common.amountVnd(),
                common.currency(), common.completedAt(), common.errorCode());
        Instant now = clock.instant();
        if (contradictsFailure(refund, attempt, response)) {
            work.holdForReview(refund.getRefundId(), attempt.getAttemptId(), now);
            return;
        }
        if (!matches(refund, attempt, response)) {
            throw new OutcomeConflict();
        }
        if (!work.isHeldForReview(refund.getRefundId())) {
            apply(refund, payment, attempt, response, now);
        }
    }

    private void apply(Refund refund, Payment payment, RefundAttempt attempt, RefundResponse response, Instant now) {
        if (!attempt.isPending()) {
            return;
        }
        attempt.observe(response);
        UUID refundId = refund.getRefundId();
        if (attempt.isPending()) {
            work.release(refundId, now.plus(queryInterval));
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("refundId", refundId);
        payload.put("paymentId", payment.getPaymentId());
        payload.put("orderId", refund.getOrderId());
        payload.put("scope", refund.getScope().name());
        payload.put("amountVnd", refund.getAmountVnd());

        String eventType;
        if (response.status() == GatewayStatus.SUCCESS) {
            refund.markSuccess(attempt.getProviderRefundId(), attempt.getCompletedAt());
            work.release(refundId, null);
            eventType = "refund.succeeded";
        } else {
            refund.markFailed(attempt.getProviderRefundId(), attempt.getCompletedAt());
            Instant nextRetry = refund.isCustomerRetryRequired() ? null
                    : attempt.getCompletedAt().plus(RETRY_DELAYS.get(refund.getRetryCount()));
            work.release(refundId, nextRetry);
            payload.put("checkoutSessionId", payment.getCheckoutSessionId());
            payload.put("customerId", payment.getCustomerId());
            payload.put("errorCode", attempt.getErrorCode());
            payload.put("retryCycle", refund.getRetryCycle());
            payload.put("retryCount", refund.getRetryCount());
            payload.put("retryExhausted", refund.isCustomerRetryRequired());
            eventType = "refund.failed";
        }
        outbox.save(new OutboxEvent(null, eventType, refundId, attempt.eventVersion(), payload));
    }

    private boolean matches(Refund refund, RefundAttempt attempt, RefundResponse result) {
        if (refund.getAmountVnd() != result.amountVnd() || !"VND".equals(result.currency())
                || result.status() == null || result.providerRefundId() == null || result.providerRefundId().isBlank()
                || result.providerRefundId().length() > 255 || !attempt.matchesReference(result.providerRefundId())
                || (attempt.getProviderRefundId() == null
                    && attempts.existsByProviderRefundIdAndAttemptIdNot(result.providerRefundId(), attempt.getAttemptId()))) {
            return false;
        }
        if (result.status() == GatewayStatus.PENDING) {
            return attempt.isPending() && result.completedAt() == null && result.errorCode() == null;
        }
        if (result.completedAt() == null
                || (result.status() == GatewayStatus.FAILED && (result.errorCode() == null
                    || result.errorCode().isBlank() || result.errorCode().length() > 100))
                || (result.status() == GatewayStatus.SUCCESS && result.errorCode() != null)) {
            return false;
        }
        return attempt.isPending() || attempt.matchesTerminal(result);
    }

    private static boolean contradictsFailure(Refund refund, RefundAttempt attempt, RefundResponse result) {
        return attempt.getStatus() == GatewayStatus.FAILED && result != null && result.status() == GatewayStatus.SUCCESS
                && refund.getAmountVnd() == result.amountVnd() && "VND".equals(result.currency())
                && result.completedAt() != null && result.errorCode() == null
                && result.providerRefundId() != null && !result.providerRefundId().isBlank()
                && result.providerRefundId().length() <= 255;
    }

    private Payment paymentFor(Refund refund) {
        Payment payment = payments.findById(refund.getPaymentId()).orElseThrow(OutcomeConflict::new);
        if (!payment.isSuccess()
                || !"DEMO_PROVIDER".equals(payment.getProvider()) || !"VND".equals(payment.getCurrency())
                || payment.getProviderTransactionId() == null || refund.getAmountVnd() > payment.getAmountVnd()
                || (refund.getScope() == RefundScope.FULL_CHECKOUT
                    && !Objects.equals(refund.getAmountVnd(), payment.getAmountVnd()))) {
            throw new OutcomeConflict();
        }
        return payment;
    }

    private static RefundResponse send(PaymentGateway gateway, Dispatch dispatch) {
        var request = dispatch.command();
        try {
            return dispatch.action() == Action.POST ? gateway.createRefund(request)
                    : gateway.queryRefund(request.refundId(), request.attemptId());
        } catch (RefundAttemptNotFound missing) {
            var absence = missing.absence();
            return new RefundResponse(absence.providerRefundId(), GatewayStatus.FAILED, request.amountVnd(),
                    request.currency(), absence.completedAt(), absence.errorCode());
        } catch (ProviderHttpException | UncertainResultException unavailable) {
            // The provider has not confirmed a terminal result for this attempt.
            return null;
        }
    }

    private static Dispatch dispatch(Refund refund, Payment payment, RefundAttempt attempt, UUID lease, Action action) {
        return new Dispatch(new CreateRefundRequest(refund.getRefundId(), attempt.getAttemptId(), payment.getPaymentId(),
                payment.getProviderTransactionId(), refund.getAmountVnd(), payment.getCurrency()), lease, action);
    }

    private enum Action { POST, QUERY, VERIFY_FAILURE }

    private record Dispatch(CreateRefundRequest command, UUID lease, Action action) { }
}
