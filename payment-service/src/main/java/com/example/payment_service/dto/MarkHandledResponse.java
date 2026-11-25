package com.example.payment_service.dto;

import java.time.Instant;
import java.util.UUID;

public record MarkHandledResponse(
        UUID checkoutSessionId,
        String manualNote,
        Instant handledAt
) {
}
