package com.example.payment_service.client;

import java.util.Objects;

public record ChargeResult(
        GatewayStatus status,
        String providerTransactionId,
        String errorCode
) {
    public ChargeResult {
        Objects.requireNonNull(status, "status is required");
    }

    public static ChargeResult success(String providerTransactionId) {
        Objects.requireNonNull(providerTransactionId, "providerTransactionId is required on SUCCESS");
        return new ChargeResult(GatewayStatus.SUCCESS, providerTransactionId, null);
    }

    public static ChargeResult failed(String errorCode, String providerTransactionId) {
        return new ChargeResult(GatewayStatus.FAILED, providerTransactionId, errorCode);
    }

    public static ChargeResult pending(String providerTransactionId) {
        return new ChargeResult(GatewayStatus.PENDING, providerTransactionId, null);
    }

    public boolean isSuccess() {
        return status == GatewayStatus.SUCCESS;
    }

    public boolean isFailed() {
        return status == GatewayStatus.FAILED;
    }

    public boolean isPending() {
        return status == GatewayStatus.PENDING;
    }
}
