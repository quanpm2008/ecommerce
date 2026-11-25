package com.example.payment_service.service;

import com.example.payment_service.client.ChargeCommand;
import com.example.payment_service.client.ChargeResult;
import com.example.payment_service.client.PaymentGateway;
import com.example.payment_service.client.SimulatorScenario;
import com.example.payment_service.entity.OutboxEvent;
import com.example.payment_service.entity.Payment;
import com.example.payment_service.exception.IdempotencyConflictException;
import com.example.payment_service.exception.PaymentConflictException;
import com.example.payment_service.exception.PaymentNotFoundException;
import com.example.payment_service.repository.OutboxEventRepository;
import com.example.payment_service.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentGateway paymentGateway;

    public PaymentService(PaymentRepository paymentRepository,
                          OutboxEventRepository outboxEventRepository,
                          PaymentGateway paymentGateway) {
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.paymentGateway = paymentGateway;
    }

    public Payment processPayment(UUID checkoutSessionId, UUID customerId, Long amountVnd,
                                  String idempotencyKey, SimulatorScenario scenario) {
        Objects.requireNonNull(checkoutSessionId, "checkoutSessionId is required");
        Objects.requireNonNull(customerId, "customerId is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");

        Optional<Payment> existingByKey = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByKey.isPresent()) {
            Payment existing = existingByKey.get();

            if (existing.getCheckoutSessionId().equals(checkoutSessionId)
                    && Objects.equals(existing.getAmountVnd(), amountVnd)) {
                log.info("[PaymentService] Idempotent replay for key={}, paymentId={}", idempotencyKey, existing.getPaymentId());
                return existing;
            }
            // Khác payload: vi phạm chống trùng lặp
            throw new IdempotencyConflictException("IDEMPOTENCY_KEY_REUSED",
                    "Idempotency key has already been used for a different payment request");
        }

        // Kiểm tra xem CheckoutSession đã có Payment nào chưa (tối đa 1 Payment/phiên)
        Optional<Payment> existingBySession = paymentRepository.findByCheckoutSessionId(checkoutSessionId);
        if (existingBySession.isPresent()) {
            Payment sessionPayment = existingBySession.get();
            if (sessionPayment.isPending()) {
                throw new PaymentConflictException("PAYMENT_IN_PROGRESS",
                        "A payment for this checkout session is already in progress");
            }
            throw new PaymentConflictException("CHECKOUT_NOT_PAYABLE",
                    "Checkout session already has a completed or failed payment");
        }

        Payment pendingPayment = createPendingPaymentInNewTransaction(
                checkoutSessionId, customerId, amountVnd, idempotencyKey
        );

        ChargeCommand chargeCmd = new ChargeCommand(idempotencyKey, amountVnd, checkoutSessionId, scenario);
        ChargeResult chargeResult;
        try {
            chargeResult = paymentGateway.charge(chargeCmd);
        } catch (Exception ex) {
            log.error("[PaymentService] Error communicating with PaymentGateway: {}", ex.getMessage(), ex);
            // Mất kết nối/timeout: giữ Payment ở trạng thái PENDING để xử lý sau
            chargeResult = ChargeResult.pending(null);
        }

        return applyGatewayResult(pendingPayment.getPaymentId(), chargeResult);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment createPendingPaymentInNewTransaction(UUID checkoutSessionId, UUID customerId,
                                                        Long amountVnd, String idempotencyKey) {
        Payment payment = new Payment(
                checkoutSessionId, customerId, amountVnd, "VND", "SIMULATOR", "DEV", idempotencyKey
        );
        return paymentRepository.saveAndFlush(payment);
    }

    @Transactional
    public Payment applyGatewayResult(UUID paymentId, ChargeResult result) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));

        if (!payment.isPending()) {
            log.warn("[PaymentService] Payment {} already transitioned to {}, ignoring result", paymentId, payment.getStatus());
            return payment;
        }

        Instant now = Instant.now();
        if (result.isSuccess()) {
            payment.markSuccess(result.providerTransactionId(), now);
            paymentRepository.save(payment);

            // Ghi Outbox Event: payment.succeeded
            Map<String, Object> payload = Map.of(
                    "paymentId", payment.getPaymentId().toString(),
                    "checkoutSessionId", payment.getCheckoutSessionId().toString(),
                    "customerId", payment.getCustomerId().toString(),
                    "amountVnd", payment.getAmountVnd(),
                    "currency", payment.getCurrency(),
                    "providerTransactionId", payment.getProviderTransactionId(),
                    "completedAt", now.toString()
            );
            outboxEventRepository.save(new OutboxEvent(UUID.randomUUID(), "payment.succeeded", payment.getPaymentId(), payload));
            log.info("[PaymentService] Payment SUCCESS for paymentId={}, txId={}", paymentId, payment.getProviderTransactionId());

        } else if (result.isFailed()) {
            payment.markFailed(result.errorCode(), result.providerTransactionId(), now);
            paymentRepository.save(payment);

            // Ghi Outbox Event: payment.failed
            Map<String, Object> payload = new HashMap<>();
            payload.put("paymentId", payment.getPaymentId().toString());
            payload.put("checkoutSessionId", payment.getCheckoutSessionId().toString());
            payload.put("customerId", payment.getCustomerId().toString());
            payload.put("amountVnd", payment.getAmountVnd());
            payload.put("errorCode", result.errorCode());
            payload.put("providerTransactionId", result.providerTransactionId());
            payload.put("completedAt", now.toString());

            outboxEventRepository.save(new OutboxEvent(UUID.randomUUID(), "payment.failed", payment.getPaymentId(), payload));
            log.info("[PaymentService] Payment FAILED for paymentId={}, error={}", paymentId, result.errorCode());

        } else {
            log.info("[PaymentService] Payment PENDING for paymentId={}", paymentId);
        }

        return payment;
    }

    @Transactional(readOnly = true)
    public Optional<Payment> getPaymentByCheckoutSession(UUID checkoutSessionId, UUID currentCustomerId) {
        Optional<Payment> paymentOpt = paymentRepository.findByCheckoutSessionId(checkoutSessionId);
        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            // Xác minh ownership: Không cho phép đọc phiên của người khác
            if (currentCustomerId != null && !payment.getCustomerId().equals(currentCustomerId)) {
                throw new PaymentNotFoundException("Checkout session payment not found for current customer");
            }
        }
        return paymentOpt;
    }
}
