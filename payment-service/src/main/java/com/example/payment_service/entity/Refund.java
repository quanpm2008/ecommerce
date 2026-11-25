package com.example.payment_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "refund_id", updatable = false, nullable = false)
    private UUID refundId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private RefundScope scope;

    @Column(name = "amount_vnd", nullable = false)
    private Long amountVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private RefundReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RefundStatus status = RefundStatus.PENDING;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "provider_refund_id", length = 255)
    private String providerRefundId;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "retry_cycle", nullable = false)
    private int retryCycle = 0;

    @Column(name = "customer_retry_required", nullable = false)
    private boolean customerRetryRequired = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Tạo nghĩa vụ hoàn tiền theo đơn hàng ORDER
     */
    public static Refund createOrderRefund(UUID paymentId, UUID orderId, Long amountVnd,
                                           RefundReason reason, String idempotencyKey) {
        Objects.requireNonNull(orderId, "orderId is required for ORDER refund scope");
        return new Refund(paymentId, orderId, RefundScope.ORDER, amountVnd, reason, idempotencyKey);
    }

    /**
     * Tạo nghĩa vụ hoàn tiền toàn bộ phiên FULL_CHECKOUT
     */
    public static Refund createFullCheckoutRefund(UUID paymentId, Long amountVnd, String idempotencyKey) {
        return new Refund(paymentId, null, RefundScope.FULL_CHECKOUT, amountVnd, RefundReason.LATE_PAYMENT, idempotencyKey);
    }

    private Refund(UUID paymentId, UUID orderId, RefundScope scope, Long amountVnd,
                   RefundReason reason, String idempotencyKey) {
        if (amountVnd == null || amountVnd <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than 0");
        }
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId is required");
        this.orderId = orderId;
        this.scope = Objects.requireNonNull(scope, "scope is required");
        this.amountVnd = amountVnd;
        this.reason = Objects.requireNonNull(reason, "reason is required");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        this.status = RefundStatus.PENDING;
        this.retryCount = 0;
        this.retryCycle = 0;
        this.customerRetryRequired = false;
    }

    /**
     * Cập nhật thành công từ Gateway.
     */
    public void markSuccess(String providerRefundId, Instant completedAt) {
        this.status = RefundStatus.SUCCESS;
        this.providerRefundId = providerRefundId;
        this.completedAt = completedAt != null ? completedAt : Instant.now();
        this.customerRetryRequired = false;
    }

    /**
     * Cập nhật thất bại từ Gateway.
     */
    public void markFailed(String providerRefundId, Instant completedAt) {
        this.status = RefundStatus.FAILED;
        this.providerRefundId = providerRefundId;
        this.completedAt = completedAt != null ? completedAt : Instant.now();
    }

    /**
     * Hết 3 lần retry tự động trong chu kỳ mà vẫn thất bại -> đánh dấu cần Customer can thiệp mở chu kỳ mới.
     */
    public void markCustomerRetryRequired() {
        this.status = RefundStatus.FAILED;
        this.customerRetryRequired = true;
    }

    /**
     * Thử lại tự động trong chu kỳ (tối đa 3 lần).
     */
    public void autoRetry() {
        if (this.status != RefundStatus.FAILED) {
            throw new IllegalStateException("Only FAILED refund can be retried (current status: " + this.status + ")");
        }
        if (this.retryCount >= 3) {
            throw new IllegalStateException("Maximum auto retries (3) reached in current cycle");
        }
        this.status = RefundStatus.PENDING;
        this.retryCount++;
        this.completedAt = null;
    }

    /**
     * Customer chủ Payment mở chu kỳ hoàn tiền mới.
     */
    public void openNewRetryCycle(int expectedCycle) {
        if (this.status != RefundStatus.FAILED) {
            throw new IllegalStateException("Only FAILED refund can open a new retry cycle");
        }
        if (!this.customerRetryRequired) {
            throw new IllegalStateException("Customer retry is not required yet for this refund");
        }
        if (this.retryCycle != expectedCycle) {
            throw new IllegalStateException("Snapshot mismatch: expected retryCycle " + this.retryCycle + ", got " + expectedCycle);
        }
        this.retryCycle++;
        this.retryCount = 0;
        this.customerRetryRequired = false;
        this.status = RefundStatus.PENDING;
        this.completedAt = null;
    }
}

