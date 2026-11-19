package com.example.payment_service.client;

import java.util.Objects;

public record QueryResult(
        GatewayStatus status,
        String providerId,
        String errorCode
) {
    public QueryResult {
        Objects.requireNonNull(status, "status is required");
    }

    public static QueryResult of(GatewayStatus status, String providerId, String errorCode) {
        return new QueryResult(status, providerId, errorCode);
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
