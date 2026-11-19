package com.example.payment_service.client;

import java.util.Objects;

public record RefundResult(
        GatewayStatus status,
        String providerRefundId,
        String errorCode
) {
    public RefundResult {
        Objects.requireNonNull(status, "status is required");
    }

    public static RefundResult success(String providerRefundId) {
        Objects.requireNonNull(providerRefundId, "providerRefundId is required on SUCCESS");
        return new RefundResult(GatewayStatus.SUCCESS, providerRefundId, null);
    }

    public static RefundResult failed(String errorCode, String providerRefundId) {
        return new RefundResult(GatewayStatus.FAILED, providerRefundId, errorCode);
    }

    public static RefundResult pending(String providerRefundId) {
        return new RefundResult(GatewayStatus.PENDING, providerRefundId, null);
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
