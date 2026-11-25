package com.example.payment_service.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import com.example.payment_service.client.GatewayStatus;
import com.example.payment_service.client.ProviderContracts.RefundResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "payment_refund_attempts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundAttempt {
    @Id
    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;
    @Column(name = "refund_id", nullable = false, updatable = false)
    private UUID refundId;
    @Column(name = "retry_cycle", nullable = false, updatable = false)
    private int retryCycle;
    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GatewayStatus status;
    @Column(name = "provider_refund_id", length = 255)
    private String providerRefundId;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "error_code", length = 100)
    private String errorCode;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RefundAttempt(Refund refund, Instant now) {
        this.attemptId = UUID.randomUUID();
        this.refundId = refund.getRefundId();
        this.retryCycle = refund.getRetryCycle();
        this.attemptNumber = refund.getRetryCount();
        this.status = GatewayStatus.PENDING;
        this.createdAt = now;
    }

    public boolean matchesReference(String providerReference) {
        return providerRefundId == null || providerRefundId.equals(providerReference);
    }

    public boolean matchesTerminal(RefundResponse result) {
        return status == result.status() && matchesReference(result.providerRefundId())
                && completedAt.equals(result.completedAt().truncatedTo(ChronoUnit.MICROS))
                && Objects.equals(errorCode, result.errorCode());
    }

    public void observe(RefundResponse result) {
        providerRefundId = result.providerRefundId();
        if (result.status() != GatewayStatus.PENDING) {
            status = result.status();
            completedAt = result.completedAt().truncatedTo(ChronoUnit.MICROS);
            errorCode = result.errorCode();
        }
    }

    public boolean isPending() {
        return status == GatewayStatus.PENDING;
    }

    public long eventVersion() {
        return retryCycle * 4L + attemptNumber + 1;
    }
}
