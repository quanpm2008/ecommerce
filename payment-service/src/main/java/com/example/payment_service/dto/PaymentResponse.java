package com.example.payment_service.dto;

import com.example.payment_service.entity.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        UUID checkoutSessionId,
        Long amountVnd,
        String currency,
        String provider,
        PaymentStatus status,
        String providerTransactionId,
        String errorCode,
        Long refundedAmountVnd,
        String refundState,
        Instant createdAt,
        Instant completedAt,
        boolean isSimulated
) {
}
