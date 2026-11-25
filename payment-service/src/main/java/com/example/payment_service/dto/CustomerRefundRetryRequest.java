package com.example.payment_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CustomerRefundRetryRequest(
        @NotNull(message = "expectedRetryCycle is required")
        @Min(value = 0, message = "expectedRetryCycle must be greater than or equal to 0")
        Integer expectedRetryCycle
) {
}
