package com.example.simulator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SimulatorModels {
    private SimulatorModels() {
    }

    public enum GatewayStatus {
        PENDING,
        SUCCESS,
        FAILED
    }

    public enum FaultMode {
        NONE,
        REJECT,
        PENDING,
        DROP_AFTER_COMMIT,
        DELAY_AFTER_COMMIT,
        LATE_SUCCESS
    }

    public record CreatePaymentRequest(
            UUID paymentId,
            UUID checkoutSessionId,
            long amountVnd,
            String currency,
            Instant expiresAt
    ) {
    }

    public record CreatePaymentResponse(
            String providerTransactionId,
            GatewayStatus status,
            Instant expiresAt,
            String paymentUrl
    ) {
    }

    public record PaymentQueryResponse(
            String providerTransactionId,
            long amountVnd,
            String currency,
            GatewayStatus status,
            Instant expiresAt,
            String paymentUrl,
            Instant completedAt,
            String errorCode
    ) {
    }

    public record CreateRefundRequest(
            UUID refundId,
            UUID attemptId,
            UUID paymentId,
            String providerTransactionId,
            long amountVnd,
            String currency
    ) {
    }

    public record RefundResponse(
            String providerRefundId,
            GatewayStatus status,
            long amountVnd,
            String currency,
            Instant completedAt,
            String errorCode
    ) {
    }

    public record RefundAttemptAbsence(
            UUID refundId,
            UUID attemptId,
            String providerRefundId,
            Instant completedAt,
            String errorCode
    ) {
    }

    public static final class PaymentRecord {
        public UUID paymentId;
        public UUID checkoutSessionId;
        public long amountVnd;
        public String currency;
        public Instant expiresAt;
        public String providerTransactionId;
        public String token;
        public GatewayStatus status;
        public Instant completedAt;
        public String errorCode;
        public UUID lastEventId;

        public PaymentRecord() {
        }

        public PaymentRecord(CreatePaymentRequest request, String token) {
            this.paymentId = request.paymentId();
            this.checkoutSessionId = request.checkoutSessionId();
            this.amountVnd = request.amountVnd();
            this.currency = request.currency();
            this.expiresAt = request.expiresAt();
            this.providerTransactionId = "payment-" + request.paymentId();
            this.token = token;
            this.status = GatewayStatus.PENDING;
        }
    }

    public static final class RefundAttemptRecord {
        public UUID refundId;
        public UUID attemptId;
        public UUID paymentId;
        public String providerTransactionId;
        public long amountVnd;
        public String currency;
        public String providerRefundId;
        public GatewayStatus status;
        public Instant completedAt;
        public String errorCode;
        public UUID lastEventId;

        public RefundAttemptRecord() {
        }

        public RefundAttemptRecord(CreateRefundRequest request, GatewayStatus status, Instant completedAt, String errorCode) {
            this.refundId = request.refundId();
            this.attemptId = request.attemptId();
            this.paymentId = request.paymentId();
            this.providerTransactionId = request.providerTransactionId();
            this.amountVnd = request.amountVnd();
            this.currency = request.currency();
            this.providerRefundId = "refund-" + request.attemptId();
            this.status = status;
            this.completedAt = completedAt;
            this.errorCode = errorCode;
        }

        public RefundResponse toResponse() {
            return new RefundResponse(providerRefundId, status, amountVnd, currency, completedAt, errorCode);
        }
    }

    public record SnapshotState(
            List<PaymentRecord> payments,
            List<RefundAttemptRecord> refunds,
            List<RefundAttemptAbsence> closedMissingAttempts,
            FaultMode nextPaymentFault,
            FaultMode nextRefundFault,
            long lateSuccessDelayMs
    ) {
    }

    public record ScenarioControlRequest(
            FaultMode nextPaymentFault,
            FaultMode nextRefundFault,
            Long lateSuccessDelayMs
    ) {
    }

    public record CompleteRequest(
            GatewayStatus status,
            String errorCode
    ) {
    }
}
