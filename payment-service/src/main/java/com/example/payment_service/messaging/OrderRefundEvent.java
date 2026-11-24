package com.example.payment_service.messaging;

import com.example.payment_service.entity.RefundReason;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class OrderRefundEvent {
    private OrderRefundEvent() {}

    public record Paid(UUID eventId, UUID checkoutId, UUID paymentId, UUID customerId, Map<UUID, Line> orders) {}
    public record Line(UUID sellerId, long totalAmountVnd) {}
    public record Cancelled(UUID eventId, UUID checkoutId, UUID orderId, UUID customerId, UUID sellerId,
            long amountVnd, RefundReason reason) {}

    static Paid paid(JsonNode root, String key) {
        var payload = root.path("payload");
        UUID checkout = id(payload, "checkoutSessionId");
        UUID event = envelope(root, key, checkout);
        Instant.parse(payload.path("paidAt").asString());
        var orders = new HashMap<UUID, Line>();
        if (!payload.path("orders").isArray()) {
            throw new IllegalArgumentException("Missing paid orders");
        }
        for (var line : payload.path("orders")) {
            var value = new Line(id(line, "sellerId"), amount(line, "totalAmountVnd"));
            if (orders.putIfAbsent(id(line, "orderId"), value) != null) {
                throw new IllegalArgumentException("Duplicate paid order");
            }
        }
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("Missing paid orders");
        }
        return new Paid(event, checkout, id(payload, "paymentId"), id(payload, "customerId"), Map.copyOf(orders));
    }

    static Cancelled cancelled(JsonNode root, String key) {
        var payload = root.path("payload");
        UUID order = id(payload, "orderId");
        UUID event = envelope(root, key, order);
        if (!payload.path("wasPaid").isBoolean()) {
            throw new IllegalArgumentException("Missing wasPaid");
        }
        if (!payload.path("wasPaid").asBoolean()) {
            return null;
        }
        var reason = RefundReason.valueOf(payload.path("refundReason").asString());
        if (reason == RefundReason.LATE_PAYMENT) {
            throw new IllegalArgumentException("Invalid ORDER refund reason");
        }
        return new Cancelled(event, id(payload, "checkoutSessionId"), order, id(payload, "customerId"),
                id(payload, "sellerId"), amount(payload, "totalAmountVnd"), reason);
    }

    private static UUID envelope(JsonNode root, String key, UUID aggregate) {
        var version = root.path("version");
        if (!"order-service".equals(root.path("producer").asString(null))
                || !aggregate.equals(id(root, "aggregateId")) || !aggregate.toString().equals(key)
                || !version.isIntegralNumber() || !version.canConvertToLong() || version.asLong() < 1) {
            throw new IllegalArgumentException("Invalid Order event envelope");
        }
        Instant.parse(root.path("occurredAt").asString());
        return id(root, "eventId");
    }

    private static UUID id(JsonNode node, String field) {
        String value = node.path(field).asString("");
        if (value.length() != 36) {
            throw new IllegalArgumentException("Invalid UUID: " + field);
        }
        return UUID.fromString(value);
    }

    private static long amount(JsonNode node, String field) {
        var value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() <= 0) {
            throw new IllegalArgumentException("Invalid amount: " + field);
        }
        return value.asLong();
    }
}
