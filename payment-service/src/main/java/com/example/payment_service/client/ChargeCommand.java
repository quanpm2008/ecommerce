package com.example.payment_service.client;

import java.util.Objects;
import java.util.UUID;

public record ChargeCommand(
        String idempotencyKey,
        Long amountVnd,
        UUID checkoutSessionId,
        SimulatorScenario scenario
) {
    public ChargeCommand {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(checkoutSessionId, "checkoutSessionId is required");

        if (amountVnd == null || amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be greater than 0");
        }
    }

    public ChargeCommand(String idempotencyKey, Long amountVnd, UUID checkoutSessionId, String scenarioName) {
        this(
                idempotencyKey,
                amountVnd,
                checkoutSessionId,
                scenarioName != null ? SimulatorScenario.valueOf(scenarioName.toUpperCase()) : SimulatorScenario.SUCCESS
        );
    }
}
