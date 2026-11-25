package com.example.notification_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.notification_service.service.NotificationEventHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NotificationEndpointsTests extends IntegrationTestSupport {
    private static final UUID CUSTOMER = UUID.fromString("11000000-0000-4000-8000-000000000001");
    private static final UUID SELLER = UUID.fromString("22000000-0000-4000-8000-000000000001");
    private static final UUID OTHER = UUID.fromString("33000000-0000-4000-8000-000000000001");
    private static final UUID ORDER = UUID.fromString("44000000-0000-4000-8000-000000000001");
    private static final UUID CHECKOUT = UUID.fromString("55000000-0000-4000-8000-000000000001");
    private static final String BASE = "/api/v1/notifications/me";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired NotificationEventHandler events;

    @Test
    void actualHttpCreateReplayReadAndDeleteRemainOwnerScoped() throws Exception {
        session("seller", SELLER, "SELLER", "ACTIVE", "ACTIVE", true, true);
        session("other", OTHER, "SELLER", "ACTIVE", "ACTIVE", true, true);
        Map<String,Object> body = product("product-event", SELLER);
        var created = call("POST", "/internal/v1/notifications", null, "product-service", body);
        assertThat(created.statusCode()).isEqualTo(201);
        String id = tree(created).path("data").path("notificationIds").get(0).stringValue();
        assertThat(call("POST", "/internal/v1/notifications", null, "product-service", body).statusCode()).isEqualTo(200);
        var list = tree(call("GET", BASE, "seller", null, null));
        assertThat(list.path("meta").path("totalItems").longValue()).isEqualTo(1);
        assertThat(list.path("data").get(0).path("content").stringValue()).contains("Sản phẩm thử");
        assertThat(list.path("data").get(0).has("recipientUserId")).isFalse();
        assertThat(tree(call("GET", BASE, "other", null, null)).path("data").size()).isZero();
        assertThat(call("POST", BASE + "/" + id + "/read", "other", null, null).statusCode()).isEqualTo(404);
        assertThat(call("DELETE", BASE + "/" + id, "other", null, null).statusCode()).isEqualTo(404);
        assertThat(tree(call("GET", BASE + "/unread-count", "seller", null, null)).path("data").path("unreadCount").longValue()).isEqualTo(1);
        assertThat(call("POST", BASE + "/" + id + "/read", "seller", null, null).statusCode()).isEqualTo(204);
        assertThat(tree(call("GET", BASE + "?unreadOnly=true", "seller", null, null)).path("data").size()).isZero();
        assertThat(call("POST", BASE + "/read-all", "seller", null, null).statusCode()).isEqualTo(204);
        assertThat(call("DELETE", BASE + "/" + id, "seller", null, null).statusCode()).isEqualTo(204);
        assertThat(call("DELETE", BASE + "/" + id, "seller", null, null).statusCode()).isEqualTo(204);
        assertThat(call("POST", "/internal/v1/notifications", null, "product-service", body).statusCode()).isEqualTo(200);
        assertThat(tree(call("GET", BASE, "seller", null, null)).path("data").size()).isZero();
        body.put("templateData", Map.of("productName", "Changed", "reason", "vi phạm"));
        assertThat(call("POST", "/internal/v1/notifications", null, "product-service", body).statusCode()).isEqualTo(409);
    }

    @Test
    void liveAuthorizationFailsClosedAndDoesNotTrustUserHeaders() throws Exception {
        session("active", CUSTOMER, "CUSTOMER", "ACTIVE", "ACTIVE", true, true);
        assertThat(call("GET", BASE, null, null, null).statusCode()).isEqualTo(401);
        assertThat(tree(call("GET", BASE, "expired", null, null)).path("error").path("code").stringValue()).isEqualTo("TOKEN_EXPIRED");
        assertThat(call("GET", BASE, "active", null, null).statusCode()).isEqualTo(200);
        session("active", CUSTOMER, "CUSTOMER", "ACTIVE", "ACTIVE", false, true);
        assertThat(call("GET", BASE, "active", null, null).statusCode()).isEqualTo(401);
        session("active", CUSTOMER, "CUSTOMER", "DISABLED", "DISABLED", true, true);
        assertThat(call("GET", BASE, "active", null, null).statusCode()).isEqualTo(403);
        session("active", CUSTOMER, "CUSTOMER", "BANNED", "ACTIVE", true, false);
        assertThat(call("GET", BASE, "active", null, null).statusCode()).isEqualTo(403);
        session("active", CUSTOMER, "CUSTOMER", "BANNED", "ACTIVE", true, true);
        assertThat(call("GET", BASE, "active", null, null).statusCode()).isEqualTo(200);
        SESSIONS.put("Bearer active", "{\"data\":null}");
        assertThat(call("GET", BASE, "active", null, null).statusCode()).isEqualTo(503);
    }

    @Test
    void untrustedProducerWrongTypeUnknownRecipientAndSecretsCannotCreateRows() throws Exception {
        RECIPIENTS.put(SELLER, "SELLER");
        var body = product("invalid-create", SELLER);
        assertThat(call("POST", "/internal/v1/notifications", null, null, body).statusCode()).isEqualTo(401);
        assertThat(call("POST", "/internal/v1/notifications", null, "order-service", body).statusCode()).isEqualTo(403);
        body.put("templateData", Map.of("productName", "test", "resetUrl", "secret-token"));
        assertThat(call("POST", "/internal/v1/notifications", null, "product-service", body).statusCode()).isEqualTo(400);
        body = product("unknown-recipient", OTHER);
        assertThat(call("POST", "/internal/v1/notifications", null, "product-service", body).statusCode()).isEqualTo(404);
        body = product("wrong-audience", SELLER);
        body.put("audienceContext", "CUSTOMER");
        assertThat(call("POST", "/internal/v1/notifications", null, "product-service", body).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isZero();
    }

    @Test
    void checkoutFansOutWithoutLeakingAnotherSellersOrderAndEventReplayIsAtomic() throws Exception {
        UUID seller2 = OTHER;
        UUID order2 = UUID.randomUUID();
        RECIPIENTS.put(CUSTOMER, "CUSTOMER");
        RECIPIENTS.put(SELLER, "SELLER");
        RECIPIENTS.put(seller2, "SELLER");
        String event = event("checkout-1", "checkout.paid", "order-service", Map.of(
                "checkoutSessionId", CHECKOUT, "customerId", CUSTOMER,
                "orders", List.of(Map.of("orderId", ORDER, "sellerId", SELLER, "sellerName", "Shop A", "totalAmountVnd", 100),
                        Map.of("orderId", order2, "sellerId", seller2, "sellerName", "Shop B", "totalAmountVnd", 200))));
        events.handle("checkout.paid", event);
        events.handle("checkout.paid", event);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT content FROM notifications WHERE recipient_user_id=?", String.class, CUSTOMER)).contains("Shop A", "Shop B", "300đ");
        assertThat(jdbc.queryForObject("SELECT content FROM notifications WHERE recipient_user_id=?", String.class, SELLER)).contains(ORDER.toString(), "Shop A").doesNotContain(order2.toString(), "Shop B");
        assertThat(jdbc.queryForObject("SELECT state FROM notification_inbox", String.class)).isEqualTo("APPLIED");
        // Invalid second seller aborts the entire event before any recipient notification is inserted.
        RECIPIENTS.remove(seller2);
        events.handle("checkout.paid", event.replace("checkout-1", "checkout-2"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT state FROM notification_inbox WHERE event_id='checkout-2'", String.class)).isEqualTo("FAILED_REVIEW");
    }

    @Test
    void refundBusinessDedupeAndRestrictedAccountMessageRemainTruthful() throws Exception {
        RECIPIENTS.put(CUSTOMER, "CUSTOMER");
        UUID refund = UUID.randomUUID();
        Map<String,Object> payload = new LinkedHashMap<>(Map.of("refundId", refund, "paymentId", UUID.randomUUID(),
                "checkoutSessionId", CHECKOUT, "customerId", CUSTOMER, "scope", "FULL_CHECKOUT", "amountVnd", 900,
                "retryCycle", 1, "retryExhausted", false));
        events.handle("refund.failed", event("refund-pending", "refund.failed", "payment-service", payload));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isZero();
        payload.put("retryExhausted", true);
        events.handle("refund.failed", event("refund-exhausted", "refund.failed", "payment-service", payload));
        events.handle("refund.failed", event("refund-republished", "refund.failed", "payment-service", payload));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT content FROM notifications", String.class)).contains("Thử lại hoàn tiền", "Giả lập", "toàn bộ phiên");
        payload.put("retryCycle", 2);
        events.handle("refund.failed", event("refund-cycle2", "refund.failed", "payment-service", payload));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isEqualTo(2);
        events.handle("user.enabled", event("limited", "user.enabled", "identity-service", Map.of("userId", CUSTOMER,
                "accountStatus", "BANNED", "accessMode", "CUSTOMER_ORDERS_ONLY")));
        assertThat(jdbc.queryForObject("SELECT title FROM notifications WHERE notification_type='USER_ENABLED'", String.class)).isEqualTo("Mở lại quyền xử lý nghĩa vụ");
        assertThat(jdbc.queryForObject("SELECT content FROM notifications WHERE notification_type='USER_ENABLED'", String.class)).contains("vẫn bị cấm", "không khôi phục");
        events.handle("password.reset_requested", event("secret", "password.reset_requested", "identity-service", Map.of("resetUrl", "secret-token")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM notification_inbox WHERE event_id='secret'", String.class)).isEqualTo("INVALID_EVENT_ENVELOPE");
    }

    private Map<String,Object> product(String id, UUID recipient) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("eventId", id);
        result.put("recipientUserId", recipient);
        result.put("audienceContext", "SELLER");
        result.put("type", "PRODUCT_REMOVED");
        result.put("channels", List.of("IN_APP"));
        result.put("templateData", Map.of("productName", "Sản phẩm thử", "reason", "vi phạm"));
        return result;
    }

    private String event(String id, String type, String producer, Map<String,Object> payload) {
        return json.writeValueAsString(Map.of("eventId", id, "eventType", type, "producer", producer,
                "aggregateId", CHECKOUT.toString(), "version", 1, "occurredAt", NOW.toString(), "payload", payload));
    }

    private HttpResponse<String> call(String method, String path, String token, String producer, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Request-Id", "notification-test").header("X-User-Id", OTHER.toString());
        if (token != null) { request.header("Authorization", "Bearer " + token); }
        if (producer != null) { request.header("X-Service-Token", producer + "-test-0123456789abcdef"); }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (body != null) { request.header("Content-Type", "application/json"); }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode tree(HttpResponse<String> response) { return json.readTree(response.body()); }
}
