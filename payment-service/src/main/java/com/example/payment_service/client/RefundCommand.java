package com.example.payment_service.client;

import java.util.Objects;

public record RefundCommand(
        String idempotencyKey,
        String originalProviderTransactionId,
        Long amountVnd
) {
    public RefundCommand {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(originalProviderTransactionId, "originalProviderTransactionId is required");
        if (amountVnd == null || amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be greater than 0");
        }
    }
}
