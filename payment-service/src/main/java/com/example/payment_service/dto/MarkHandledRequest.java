package com.example.payment_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MarkHandledRequest(
        @NotBlank(message = "note is required")
        @Size(min = 1, max = 1000, message = "note must be between 1 and 1000 characters")
        String note
) {
}
