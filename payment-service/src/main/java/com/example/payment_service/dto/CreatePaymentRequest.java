package com.example.payment_service.dto;

import com.example.payment_service.client.SimulatorScenario;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull(message = "checkoutSessionId is required")
        UUID checkoutSessionId,
        SimulatorScenario simulatorScenario
) {
}
