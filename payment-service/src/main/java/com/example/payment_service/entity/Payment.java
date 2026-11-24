package com.example.payment_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_id", updatable = false, nullable = false)
    private UUID paymentId;

    @Column(name = "checkout_session_id", nullable = false, unique = true)
    private UUID checkoutSessionId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "amount_vnd", nullable = false)
    private Long amountVnd;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "provider", nullable = false)
    private String provider = "DEMO_PROVIDER";

    @Column(name = "environment", nullable = false)
    private String environment = "DEV";

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "provider_transaction_id", length = 255)
    private String providerTransactionId;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "payment_url", columnDefinition = "text")
    private String paymentUrl;

    //Khởi tạo Payment ở trạng thái PENDING trước khi gọi Gateway ngoài transaction
    public Payment(UUID checkoutSessionId, UUID customerId, Long amountVnd, String currency, String provider, String environment, String idempotencyKey) {
        if (amountVnd == null || amountVnd <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than 0");
        }
        this.checkoutSessionId = Objects.requireNonNull(checkoutSessionId, "checkoutSessionId is required");
        this.customerId = Objects.requireNonNull(customerId, "customerId is required");
        this.amountVnd = amountVnd;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        this.status = PaymentStatus.PENDING;
        if (!"VND".equals(currency)) {
            throw new IllegalArgumentException("Payment currency must be VND");
        }
        this.currency = currency;
        this.provider = Objects.requireNonNull(provider, "provider is required");
        this.environment = Objects.requireNonNull(environment, "environment is required");
    }

    //Chuyển trạng thái sang SUCCESS khi Gateway xác nhận thanh toán thành công
    public void markSuccess(String providerTransactionId, Instant completedAt) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is not PENDING (current status: " + this.status + ")");
        }

        this.status = PaymentStatus.SUCCESS;
        this.providerTransactionId = providerTransactionId;
        this.completedAt = completedAt != null ? completedAt : Instant.now();
        this.errorCode = null;
    }

    //Chuyển trạng thái sang FAILED khi Gateway từ chối thanh toán
    public void markFailed(String errorCode, String providerTransactionId, Instant completedAt) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is not PENDING (current status: " + this.status + ")");
        }

        this.status = PaymentStatus.FAILED;
        this.errorCode = errorCode;
        this.providerTransactionId = providerTransactionId;
        this.completedAt = completedAt != null ? completedAt : Instant.now();
    }

    public boolean isPending() {
        return this.status == PaymentStatus.PENDING;
    }
    public boolean isSuccess() {
        return this.status == PaymentStatus.SUCCESS;
    }
    public boolean isFailed() {
        return this.status == PaymentStatus.FAILED;
    }
}
