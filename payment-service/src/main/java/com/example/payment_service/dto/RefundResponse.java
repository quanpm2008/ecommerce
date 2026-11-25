package com.example.payment_service.dto;

import com.example.payment_service.entity.RefundReason;
import com.example.payment_service.entity.RefundScope;
import com.example.payment_service.entity.RefundStatus;

import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID refundId,
        UUID paymentId,
        UUID orderId,
        RefundScope scope,
        Long amountVnd,
        RefundReason reason,
        RefundStatus status,
        int retryCycle,
        int retryCount,
        boolean customerRetryRequired,
        boolean canRetryRefund,
        String providerRefundId,
        Instant createdAt,
        Instant completedAt,
        boolean isSimulated
) {
}
