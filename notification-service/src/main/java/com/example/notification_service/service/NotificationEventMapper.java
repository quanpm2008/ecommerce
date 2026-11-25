package com.example.notification_service.service;

import com.example.notification_service.common.error.ApiException;
import com.example.notification_service.common.error.ErrorCode;
import com.example.notification_service.model.NotificationData.CreateRequest;
import com.example.notification_service.model.NotificationData.Reference;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Maps trusted source snapshots, never another service's database or free-form notification text. */
@Component
public class NotificationEventMapper {
    public static final Map<String, String> SOURCES = Map.ofEntries(
            Map.entry("checkout.paid", "order-service"), Map.entry("checkout.closed", "order-service"),
            Map.entry("order.delivered", "order-service"), Map.entry("order.cancelled", "order-service"),
            Map.entry("order.confirmed", "order-service"), Map.entry("refund.succeeded", "payment-service"),
            Map.entry("refund.failed", "payment-service"), Map.entry("funds.available", "finance-service"),
            Map.entry("withdrawal.completed", "finance-service"), Map.entry("seller.registered", "identity-service"),
            Map.entry("seller.approved", "identity-service"), Map.entry("seller.rejected", "identity-service"),
            Map.entry("seller.banned", "identity-service"), Map.entry("seller.unbanned", "identity-service"),
            Map.entry("customer.banned", "identity-service"), Map.entry("customer.unbanned", "identity-service"),
            Map.entry("user.disabled", "identity-service"), Map.entry("user.enabled", "identity-service"),
            Map.entry("product.removed", "product-service"));

    private final JsonMapper json;

    public NotificationEventMapper(JsonMapper json) {
        this.json = json;
    }

    public List<CreateRequest> map(String eventId, String type, JsonNode payload) {
        require(payload != null && payload.isObject());
        List<CreateRequest> result = new ArrayList<>();
        switch (type) {
            case "checkout.paid" -> {
                UUID checkout = id(payload, "checkoutSessionId");
                require(payload.path("orders").isArray() && !payload.path("orders").isEmpty());
                List<Map<String, Object>> summary = new ArrayList<>();
                Set<UUID> sellers = new HashSet<>();
                Set<UUID> orders = new HashSet<>();
                long total = 0;
                for (JsonNode order : payload.path("orders")) {
                    UUID seller = id(order, "sellerId");
                    UUID orderId = id(order, "orderId");
                    require(sellers.add(seller) && orders.add(orderId));
                    Map<String, Object> data = fields(order, "orderId", "sellerName", "totalAmountVnd");
                    summary.add(data);
                    try { total = Math.addExact(total, order.path("totalAmountVnd").longValue()); }
                    catch (ArithmeticException ex) { throw invalid(); }
                    result.add(request(eventId, seller, "SELLER", "NEW_ORDER", "ORDER", orderId, null, data));
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("checkoutSessionId", checkout.toString());
                data.put("totalAmountVnd", total);
                data.put("orders", summary);
                result.add(request(eventId, id(payload, "customerId"), "CUSTOMER", "CHECKOUT_PAID",
                        "CHECKOUT_SESSION", checkout, null, data));
            }
            case "checkout.closed" -> {
                String status = text(payload, "status");
                require(Set.of("PAYMENT_FAILED", "EXPIRED", "CANCELLED").contains(status));
                // CANCELLED has no independent NotificationType in the accepted contract.
                if (!"CANCELLED".equals(status)) {
                    result.add(request(eventId, id(payload, "customerId"), "CUSTOMER",
                            "EXPIRED".equals(status) ? "CHECKOUT_EXPIRED" : "PAYMENT_FAILED",
                            "CHECKOUT_SESSION", id(payload, "checkoutSessionId"), null,
                            fields(payload, "checkoutSessionId")));
                }
            }
            case "order.delivered", "order.confirmed", "order.cancelled" -> {
                String notificationType = switch (type) {
                    case "order.delivered" -> "ORDER_DELIVERED";
                    case "order.confirmed" -> {
                        String by = text(payload, "confirmedBy");
                        require(Set.of("CUSTOMER", "SYSTEM").contains(by));
                        yield "SYSTEM".equals(by) ? "ORDER_AUTO_CONFIRMED" : "ORDER_CONFIRMED";
                    }
                    default -> {
                        String by = text(payload, "cancelledBy");
                        require(Set.of("CUSTOMER", "SELLER", "SYSTEM").contains(by));
                        yield "SELLER".equals(by) ? "ORDER_REJECTED"
                                : "SYSTEM".equals(by) ? "ORDER_AUTO_CANCELLED" : "ORDER_CANCELLED";
                    }
                };
                Map<String, Object> data = "order.cancelled".equals(type)
                        ? fields(payload, "orderId", "sellerName", "reasonCode", "reasonNote")
                        : fields(payload, "orderId", "sellerName");
                result.add(request(eventId, id(payload, "customerId"), "CUSTOMER", notificationType,
                        "ORDER", id(payload, "orderId"), null, data));
                if ("order.cancelled".equals(type)) {
                    result.add(request(eventId, id(payload, "sellerId"), "SELLER", notificationType,
                            "ORDER", id(payload, "orderId"), null, data));
                }
            }
            case "refund.succeeded", "refund.failed" -> {
                if ("refund.failed".equals(type)) {
                    require(payload.path("retryExhausted").isBoolean());
                    if (!payload.path("retryExhausted").booleanValue()) { return List.of(); }
                }
                result.add(request(eventId, id(payload, "customerId"), "CUSTOMER",
                        "refund.failed".equals(type) ? "REFUND_RETRY_REQUIRED" : "REFUND_SUCCEEDED",
                        "REFUND", id(payload, "refundId"), null,
                        fields(payload, "refundId", "paymentId", "checkoutSessionId", "orderId", "scope", "amountVnd", "retryCycle")));
            }
            case "funds.available" -> result.add(request(eventId, id(payload, "sellerId"), "SELLER",
                    "FUNDS_AVAILABLE", "ORDER", id(payload, "orderId"), null,
                    fields(payload, "orderId", "sellerAmountVnd")));
            case "withdrawal.completed" -> result.add(request(eventId, id(payload, "sellerId"), "SELLER",
                    "WITHDRAWAL_COMPLETED", "WITHDRAWAL", id(payload, "withdrawalId"),
                    text(payload, "withdrawalCode"), fields(payload, "withdrawalId", "withdrawalCode", "amountVnd", "availableAfterVnd")));
            case "seller.registered", "seller.approved", "seller.rejected" -> result.add(request(eventId,
                    id(payload, "userId"), "SELLER", type.replace('.', '_').toUpperCase(Locale.ROOT),
                    "SELLER", id(payload, "sellerId"), null, fields(payload, "reason")));
            case "seller.banned", "customer.banned", "seller.unbanned", "customer.unbanned", "user.disabled", "user.enabled" -> {
                String notificationType = type.startsWith("user.")
                        ? type.replace('.', '_').toUpperCase(Locale.ROOT)
                        : type.endsWith(".unbanned") ? "ACCOUNT_UNBANNED" : "ACCOUNT_BANNED";
                UUID user = id(payload, "userId");
                result.add(request(eventId, user, "ACCOUNT", notificationType, "USER", user, null,
                        fields(payload, "accountStatus", "accessMode", "restoreUntil", "reason")));
            }
            case "product.removed" -> result.add(request(eventId, id(payload, "sellerId"), "SELLER",
                    "PRODUCT_REMOVED", "PRODUCT", id(payload, "productId"), null,
                    fields(payload, "productName", "reason")));
            default -> throw invalid();
        }
        return result;
    }

    private static CreateRequest request(String eventId, UUID recipient, String audience, String type,
            String referenceType, UUID referenceId, String code, Map<String, Object> data) {
        return new CreateRequest(eventId, recipient, audience, type, Set.of("IN_APP"),
                new Reference(referenceType, referenceId, code), data);
    }

    private Map<String, Object> fields(JsonNode payload, String... names) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (String name : names) {
            JsonNode node = payload.get(name);
            if (node != null && !node.isNull()) { data.put(name, json.convertValue(node, Object.class)); }
        }
        return data;
    }

    private static UUID id(JsonNode node, String name) {
        try { return UUID.fromString(text(node, name)); }
        catch (IllegalArgumentException ex) { throw invalid(); }
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        require(value.isString() && !value.stringValue().isBlank());
        return value.stringValue();
    }

    private static void require(boolean condition) {
        if (!condition) { throw invalid(); }
    }

    private static ApiException invalid() {
        return ApiException.forField(ErrorCode.VALIDATION_ERROR, "event", "INVALID_EVENT_PAYLOAD");
    }
}
