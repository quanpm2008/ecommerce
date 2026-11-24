package com.example.payment_service.client;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ProviderContracts {
    public static final String REFUND_ATTEMPT_NOT_FOUND = "REFUND_ATTEMPT_NOT_FOUND";
    private ProviderContracts() {
    }

    public record CreatePaymentRequest(
            UUID paymentId, UUID checkoutSessionId, long amountVnd, String currency, Instant expiresAt
    ) {
        public CreatePaymentRequest {
            Objects.requireNonNull(paymentId, "paymentId");
            Objects.requireNonNull(checkoutSessionId, "checkoutSessionId");
            Objects.requireNonNull(expiresAt, "expiresAt");
            requireVnd(amountVnd, currency);
        }
    }

    public record CreatePaymentResponse(
            String providerTransactionId, GatewayStatus status, Instant expiresAt, String paymentUrl
    ) {
    }

    public record PaymentQueryResponse(
            String providerTransactionId, long amountVnd, String currency, GatewayStatus status,
            Instant expiresAt, String paymentUrl, Instant completedAt, String errorCode
    ) {
    }

    public record CreateRefundRequest(
            UUID refundId, UUID attemptId, UUID paymentId, String providerTransactionId, long amountVnd, String currency
    ) {
        public CreateRefundRequest {
            Objects.requireNonNull(refundId, "refundId");
            Objects.requireNonNull(attemptId, "attemptId");
            Objects.requireNonNull(paymentId, "paymentId");
            if (providerTransactionId == null || providerTransactionId.isBlank()) {
                throw new IllegalArgumentException("providerTransactionId is required");
            }
            requireVnd(amountVnd, currency);
        }
    }

    public record RefundResponse(
            String providerRefundId, GatewayStatus status, long amountVnd, String currency,
            Instant completedAt, String errorCode
    ) {
    }

    public record RefundAttemptAbsence(UUID refundId, UUID attemptId, String providerRefundId,
            Instant completedAt, String errorCode) { }

    private static void requireVnd(long amountVnd, String currency) {
        if (amountVnd <= 0 || !"VND".equals(currency)) {
            throw new IllegalArgumentException("A positive VND amount is required");
        }
    }
}
