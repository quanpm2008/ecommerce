package com.example.payment_service.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentIssueItem(
        String issueType,
        UUID paymentId,
        UUID refundId,
        UUID checkoutSessionId,
        UUID orderId,
        Long amountVnd,
        Instant since,
        Integer retryCount
) {
}
