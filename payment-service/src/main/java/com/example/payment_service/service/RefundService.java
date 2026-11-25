package com.example.payment_service.service;

import com.example.payment_service.client.*;
import com.example.payment_service.entity.*;
import com.example.payment_service.exception.*;
import com.example.payment_service.repository.OutboxEventRepository;
import com.example.payment_service.repository.PaymentRepository;
import com.example.payment_service.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentGateway paymentGateway;

    public RefundService(RefundRepository refundRepository,
                         PaymentRepository paymentRepository,
                         OutboxEventRepository outboxEventRepository,
                         PaymentGateway paymentGateway) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.paymentGateway = paymentGateway;
    }

    /**
     * Tạo yêu cầu hoàn tiền ban đầu cho đơn hàng ORDER hoặc toàn bộ phiên FULL_CHECKOUT
     */
    public Refund initiateRefund(UUID paymentId, UUID orderId, RefundScope scope,
                                 Long amountVnd, RefundReason reason, String idempotencyKey) {
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(scope, "scope is required");
        Objects.requireNonNull(amountVnd, "amountVnd is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");

        Optional<Refund> existingByKey = refundRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByKey.isPresent()) {
            Refund existing = existingByKey.get();
            if (existing.getPaymentId().equals(paymentId) && Objects.equals(existing.getAmountVnd(), amountVnd)) {
                log.info("[RefundService] Idempotent hit for refund key={}, refundId={}", idempotencyKey, existing.getRefundId());
                return existing;
            }
            throw new IdempotencyConflictException("IDEMPOTENCY_KEY_REUSED",
                    "Idempotency key has already been used for another refund request");
        }

        Refund pendingRefund = createPendingRefundInNewTransaction(
                paymentId, orderId, scope, amountVnd, reason, idempotencyKey
        );

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));

        RefundCommand cmd = new RefundCommand(idempotencyKey, payment.getProviderTransactionId(), amountVnd);
        RefundResult refundResult;
        try {
            refundResult = paymentGateway.refund(cmd);
        } catch (Exception ex) {
            log.error("[RefundService] Gateway communication error for refund: {}", ex.getMessage(), ex);
            refundResult = RefundResult.pending(null);
        }

        return applyRefundResult(pendingRefund.getRefundId(), refundResult);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Refund createPendingRefundInNewTransaction(UUID paymentId, UUID orderId, RefundScope scope,
                                                      Long amountVnd, RefundReason reason, String idempotencyKey) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));

        if (!payment.isSuccess()) {
            throw new RefundConflictException("PAYMENT_NOT_SUCCESS",
                    "Cannot refund a payment that is not in SUCCESS status (current: " + payment.getStatus() + ")");
        }

        // Điều kiện 2: Kiểm tra tổng các nghĩa vụ hoàn tiền không vượt thu
        Long currentTotalRefunded = refundRepository.sumTotalRefundAmountByPaymentId(paymentId);
        if (currentTotalRefunded + amountVnd > payment.getAmountVnd()) {
            throw new RefundLimitExceededException("REFUND_AMOUNT_EXCEEDED",
                    String.format("Total refund amount (%d + %d) exceeds payment amount (%d)",
                            currentTotalRefunded, amountVnd, payment.getAmountVnd()));
        }

        Refund refund;
        if (scope == RefundScope.ORDER) {
            if (orderId == null) {
                throw new IllegalArgumentException("orderId is required for ORDER refund scope");
            }
            if (refundRepository.findByOrderId(orderId).isPresent()) {
                throw new RefundConflictException("ORDER_ALREADY_REFUNDED",
                        "A refund obligation already exists for orderId: " + orderId);
            }
            refund = Refund.createOrderRefund(paymentId, orderId, amountVnd, reason, idempotencyKey);
        } else {
            // FULL_CHECKOUT
            refund = Refund.createFullCheckoutRefund(paymentId, amountVnd, idempotencyKey);
        }

        return refundRepository.saveAndFlush(refund);
    }

    @Transactional
    public Refund applyRefundResult(UUID refundId, RefundResult result) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new RefundNotFoundException("Refund not found with id: " + refundId));

        if (refund.getStatus() != RefundStatus.PENDING) {
            log.warn("[RefundService] Refund {} already processed with status {}, ignoring", refundId, refund.getStatus());
            return refund;
        }

        Instant now = Instant.now();
        if (result.isSuccess()) {
            refund.markSuccess(result.providerRefundId(), now);
            refundRepository.save(refund);

            // Ghi Outbox Event: refund.succeeded
            Map<String, Object> payload = Map.of(
                    "refundId", refund.getRefundId().toString(),
                    "paymentId", refund.getPaymentId().toString(),
                    "orderId", refund.getOrderId() != null ? refund.getOrderId().toString() : "",
                    "scope", refund.getScope().name(),
                    "amountVnd", refund.getAmountVnd(),
                    "reason", refund.getReason().name(),
                    "providerRefundId", refund.getProviderRefundId(),
                    "completedAt", now.toString()
            );
            outboxEventRepository.save(new OutboxEvent(UUID.randomUUID(), "refund.succeeded", refund.getRefundId(), payload));
            log.info("[RefundService] Refund SUCCESS for refundId={}, refId={}", refundId, refund.getProviderRefundId());

        } else if (result.isFailed()) {
            refund.markFailed(result.providerRefundId(), now);

            // Nếu đã đạt 3 lần retry tự động trong chu kỳ mà vẫn thất bại
            if (refund.getRetryCount() >= 3) {
                refund.markCustomerRetryRequired();
                // Phát Outbox Event thông báo cho Customer chủ Payment
                Payment payment = paymentRepository.findById(refund.getPaymentId()).orElse(null);
                String customerId = payment != null ? payment.getCustomerId().toString() : "";

                Map<String, Object> payload = Map.of(
                        "refundId", refund.getRefundId().toString(),
                        "paymentId", refund.getPaymentId().toString(),
                        "retryCycle", refund.getRetryCycle(),
                        "customerId", customerId,
                        "failedAt", now.toString()
                );
                outboxEventRepository.save(new OutboxEvent(UUID.randomUUID(), "refund.retry_required", refund.getRefundId(), payload));
                log.warn("[RefundService] Refund FAILED 3 auto retries. Customer retry required for refundId={}", refundId);
            }

            refundRepository.save(refund);

        } else {
            // PENDING/TIMEOUT: Giữ nguyên PENDING, không tăng retryCount
            log.info("[RefundService] Refund still PENDING for refundId={}", refundId);
        }

        return refund;
    }

    @Transactional
    public Refund openNewRetryCycleByCustomer(UUID refundId, int expectedRetryCycle,
                                              UUID currentCustomerId, String idempotencyKey) {
        Objects.requireNonNull(refundId, "refundId is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");

        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new RefundNotFoundException("Refund not found with id: " + refundId));

        Payment payment = paymentRepository.findById(refund.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for refund: " + refundId));

        if (currentCustomerId == null || !payment.getCustomerId().equals(currentCustomerId)) {
            throw new RefundNotFoundException("Refund obligation not found for current customer");
        }

        if (refund.getStatus() != RefundStatus.FAILED || !refund.isCustomerRetryRequired()) {
            throw new RefundRetryNotSafeException("REFUND_RETRY_NOT_SAFE",
                    "Refund is not in a state that allows opening a new retry cycle");
        }

        if (refund.getRetryCycle() != expectedRetryCycle) {
            throw new RefundRetryNotSafeException("REFUND_RETRY_NOT_SAFE",
                    String.format("Snapshot conflict: expectedRetryCycle %d does not match current %d",
                            expectedRetryCycle, refund.getRetryCycle()));
        }

        refund.openNewRetryCycle(expectedRetryCycle);
        refundRepository.save(refund);

        Map<String, Object> payload = Map.of(
                "refundId", refund.getRefundId().toString(),
                "paymentId", refund.getPaymentId().toString(),
                "newRetryCycle", refund.getRetryCycle(),
                "idempotencyKey", idempotencyKey
        );
        outboxEventRepository.save(new OutboxEvent(UUID.randomUUID(), "refund.cycle_opened", refund.getRefundId(), payload));

        log.info("[RefundService] Customer opened new retry cycle {} for refundId={}", refund.getRetryCycle(), refundId);
        return refund;
    }

    @Transactional(readOnly = true)
    public List<Refund> getRefundsByCustomer(UUID checkoutSessionId, UUID currentCustomerId) {
        Payment payment = paymentRepository.findByCheckoutSessionId(checkoutSessionId)
                .orElseThrow(() -> new PaymentNotFoundException("Checkout session payment not found"));

        if (currentCustomerId != null && !payment.getCustomerId().equals(currentCustomerId)) {
            throw new PaymentNotFoundException("Checkout session payment not found for current customer");
        }

        return refundRepository.findByPaymentId(payment.getPaymentId());
    }

    @Transactional(readOnly = true)
    public Page<Refund> getAdminRefunds(Pageable pageable) {
        return refundRepository.findAll(pageable);
    }
}
