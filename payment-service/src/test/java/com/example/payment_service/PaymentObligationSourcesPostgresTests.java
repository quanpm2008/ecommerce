package com.example.payment_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.payment_service.client.OrderCheckoutPayableClient;
import com.example.payment_service.client.OrderCheckoutPayableClient.Envelope;
import com.example.payment_service.client.OrderCheckoutPayableClient.Order;
import com.example.payment_service.client.OrderCheckoutPayableClient.Payable;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "payment.internal.order-token=phase5-order",
        "payment.internal.finance-token=phase5-finance", "payment.order.credential=phase5-payment",
        "payment.collection.initial-delay=3600000",
        "payment.refund.initial-delay=3600000", "logging.level.root=WARN"
})
class PaymentObligationSourcesPostgresTests {
    private static final String PAYMENT_URL = "https://provider.example.test/pay";
    private static final String PAYMENT_PATH = "/internal/v1/payments/by-checkout/";
    private static final String REFUNDS_PATH = "/internal/v1/payments/refunds";
    private static final String ORDER_TOKEN = "phase5-order";
    private static final Instant NOW = Instant.parse("2026-11-24T07:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plusSeconds(600);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @TestBean Clock clock;
    @MockitoBean OrderCheckoutPayableClient orderClient;

    static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @BeforeEach
    void verifyIsolatedDatabase() {
        assertThat(db.queryForObject("select current_database()", String.class)).startsWith("payment_test_");
    }

    @Test
    void paymentByCheckout() throws Exception {
        UUID checkout = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID payment = insertPayment(checkout, owner);
        String path = PAYMENT_PATH + checkout;

        JsonNode pending = data(get(path, ORDER_TOKEN));
        assertThat(pending.path("paymentId").asText()).isEqualTo(payment.toString());
        assertThat(pending.path("checkoutSessionId").asText()).isEqualTo(checkout.toString());
        assertThat(pending.path("customerId").asText()).isEqualTo(owner.toString());
        assertThat(pending.path("status").asText()).isEqualTo("PENDING");
        assertThat(pending.path("amountVnd").asLong()).isEqualTo(50_000);
        assertThat(pending.path("currency").asText()).isEqualTo("VND");
        assertThat(pending.path("expiresAt").asText()).isEqualTo(EXPIRES_AT.toString());
        assertThat(pending.path("completedAt").isNull()).isTrue();
        assertThat(pending.path("collectionClosedAt").isNull()).isTrue();
        assertThat(pending.path("paymentUrl").asText()).isEqualTo(PAYMENT_URL);

        closeCheckout(checkout);
        JsonNode closed = data(get(path, ORDER_TOKEN));
        assertThat(closed.path("status").asText()).isEqualTo("PENDING");
        assertThat(closed.path("collectionClosedAt").asText()).isEqualTo(NOW.toString());
        assertThat(closed.path("paymentUrl").isNull()).isTrue();

        markSuccessful(payment);
        JsonNode success = data(get(path, ORDER_TOKEN));
        assertThat(success.path("paymentId").asText()).isEqualTo(payment.toString());
        assertThat(success.path("customerId").asText()).isEqualTo(owner.toString());
        assertThat(success.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(success.path("completedAt").asText()).isEqualTo(NOW.toString());
        assertThat(success.path("paymentUrl").isNull()).isTrue();

        assertThat(data(get(PAYMENT_PATH + UUID.randomUUID(), ORDER_TOKEN)).isNull()).isTrue();
        assertError(get(path), 401, "UNAUTHENTICATED");
        assertError(get(path, "phase5-finance"), 401, "UNAUTHENTICATED");
        assertError(get(path, ORDER_TOKEN, ORDER_TOKEN), 401, "UNAUTHENTICATED");
        assertSourceUnavailable(path, "payments");
    }

    @Test
    void providerSessionKeepsExistingShape() throws Exception {
        UUID checkout = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID payment = insertPayment(checkout, owner);
        closeCheckout(checkout);
        when(orderClient.payable(checkout, "phase5-payment")).thenReturn(new Envelope(
                new Payable(owner, "CLOSED", EXPIRES_AT, 50_000L, List.of(new Order(UUID.randomUUID())))));

        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/internal/v1/payments/checkouts/" + checkout + "/provider-session"))
                .header("X-Service-Token", ORDER_TOKEN).POST(HttpRequest.BodyPublishers.noBody()).build();
        JsonNode created = data(send(request));
        assertThat(created.path("paymentId").asText()).isEqualTo(payment.toString());
        assertThat(created.path("checkoutSessionId").asText()).isEqualTo(checkout.toString());
        assertThat(created.path("paymentUrl").isNull()).isTrue();
        assertThat(created.has("customerId")).isFalse();
    }

    @Test
    void refundsByOrderAndCheckout() throws Exception {
        UUID orderedCheckout = UUID.randomUUID();
        UUID orderOwner = UUID.randomUUID();
        UUID orderPayment = insertPayment(orderedCheckout, orderOwner);
        markSuccessful(orderPayment);
        UUID pendingOrder = UUID.randomUUID();
        UUID failedOrder = UUID.randomUUID();
        UUID successfulOrder = UUID.randomUUID();
        UUID pendingOrderRefund = insertRefund(orderPayment, pendingOrder, "PENDING");
        UUID failedOrderRefund = insertRefund(orderPayment, failedOrder, "FAILED");
        UUID successfulOrderRefund = insertRefund(orderPayment, successfulOrder, "SUCCESS");
        insertRefund(orderPayment, UUID.randomUUID(), "SUCCESS");
        UUID otherPayment = insertPayment(UUID.randomUUID(), UUID.randomUUID());
        markSuccessful(otherPayment);
        insertRefund(otherPayment, UUID.randomUUID(), "PENDING");

        UUID pendingCheckout = UUID.randomUUID();
        UUID failedCheckout = UUID.randomUUID();
        UUID fullRefundOwner = UUID.randomUUID();
        UUID pendingFullRefund = insertFullCheckoutRefund(pendingCheckout, fullRefundOwner, "PENDING");
        UUID failedFullRefund = insertFullCheckoutRefund(failedCheckout, fullRefundOwner, "FAILED");
        insertFullCheckoutRefund(UUID.randomUUID(), UUID.randomUUID(), "SUCCESS");

        String byOrder = REFUNDS_PATH + "?orderIds=" + pendingOrder + "," + failedOrder + "," + successfulOrder;
        var orders = refundsById(byOrder, pendingOrderRefund, failedOrderRefund, successfulOrderRefund);
        var orderSnapshot = data(get(PAYMENT_PATH + orderedCheckout, ORDER_TOKEN));
        assertThat(orderSnapshot.path("customerId").asText()).isEqualTo(orderOwner.toString());
        assertRefund(orders.get(pendingOrderRefund), orderSnapshot, pendingOrder, "ORDER", "PENDING", 10_000);
        assertRefund(orders.get(failedOrderRefund), orderSnapshot, failedOrder, "ORDER", "FAILED", 10_000);
        assertRefund(orders.get(successfulOrderRefund), orderSnapshot, successfulOrder, "ORDER", "SUCCESS", 10_000);

        String byCheckout = REFUNDS_PATH + "?checkoutSessionIds=" + pendingCheckout
                + "&checkoutSessionIds=" + failedCheckout + "&checkoutSessionIds=" + orderedCheckout;
        var checkouts = refundsById(byCheckout, pendingFullRefund, failedFullRefund);
        var pendingSnapshot = data(get(PAYMENT_PATH + pendingCheckout, ORDER_TOKEN));
        var failedSnapshot = data(get(PAYMENT_PATH + failedCheckout, ORDER_TOKEN));
        for (JsonNode snapshot : List.of(pendingSnapshot, failedSnapshot)) {
            assertThat(snapshot.path("customerId").asText()).isEqualTo(fullRefundOwner.toString());
            assertThat(snapshot.path("status").asText()).isEqualTo("SUCCESS");
            assertThat(snapshot.path("collectionClosedAt").asText()).isEqualTo(NOW.toString());
        }
        assertRefund(checkouts.get(pendingFullRefund), pendingSnapshot, null, "FULL_CHECKOUT", "PENDING", 50_000);
        assertRefund(checkouts.get(failedFullRefund), failedSnapshot, null, "FULL_CHECKOUT", "FAILED", 50_000);

        refundsById(REFUNDS_PATH + "?orderIds=" + UUID.randomUUID());
        refundsById(REFUNDS_PATH + "?checkoutSessionIds=" + UUID.randomUUID());
        assertError(get(REFUNDS_PATH, ORDER_TOKEN), 400, "VALIDATION_ERROR");
        assertError(get(REFUNDS_PATH + "?orderIds=", ORDER_TOKEN), 400, "VALIDATION_ERROR");
        assertError(get(byOrder + "&checkoutSessionIds=" + pendingCheckout, ORDER_TOKEN), 400, "VALIDATION_ERROR");
        assertError(get(byOrder), 401, "UNAUTHENTICATED");
        assertError(get(byCheckout, "phase5-finance"), 401, "UNAUTHENTICATED");
        assertError(get(byCheckout, ORDER_TOKEN, ORDER_TOKEN), 401, "UNAUTHENTICATED");
        assertSourceUnavailable(byOrder, "refunds");
        assertSourceUnavailable(byCheckout, "refunds");
    }

    private UUID insertFullCheckoutRefund(UUID checkout, UUID owner, String status) {
        UUID payment = insertPayment(checkout, owner);
        closeCheckout(checkout);
        markSuccessful(payment);
        return insertRefund(payment, null, status);
    }

    private UUID insertRefund(UUID payment, UUID order, String status) {
        UUID refund = UUID.randomUUID();
        boolean fullCheckout = order == null;
        boolean failed = "FAILED".equals(status);
        Timestamp completed = "PENDING".equals(status) ? null : Timestamp.from(NOW);
        assertThat(db.update("""
                insert into payment_service.refunds
                    (refund_id, payment_id, order_id, scope, amount_vnd, reason, status, idempotency_key,
                        retry_count, customer_retry_required, completed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, refund, payment, order, fullCheckout ? "FULL_CHECKOUT" : "ORDER", fullCheckout ? 50_000 : 10_000,
                fullCheckout ? "LATE_PAYMENT" : "CUSTOMER_CANCEL", status, refund.toString(), failed ? 3 : 0,
                failed, completed)).isEqualTo(1);
        return refund;
    }

    private Map<UUID, JsonNode> refundsById(String path, UUID... expected) throws Exception {
        JsonNode refunds = data(get(path, ORDER_TOKEN));
        assertThat(refunds.isArray()).isTrue();
        assertThat(refunds.size()).isEqualTo(expected.length);
        Map<UUID, JsonNode> rows = new HashMap<>();
        for (JsonNode refund : refunds) {
            rows.put(UUID.fromString(refund.path("refundId").asText()), refund);
        }
        assertThat(rows.keySet()).containsExactlyInAnyOrder(expected);
        return rows;
    }

    private static void assertRefund(JsonNode refund, JsonNode payment, UUID order, String scope, String status,
            long amount) {
        assertThat(refund.path("paymentId").asText()).isEqualTo(payment.path("paymentId").asText());
        assertThat(refund.path("scope").asText()).isEqualTo(scope);
        assertThat(refund.path("status").asText()).isEqualTo(status);
        assertThat(refund.path("amountVnd").asLong()).isEqualTo(amount);
        if (order == null) {
            assertThat(refund.path("orderId").isNull()).isTrue();
        } else {
            assertThat(refund.path("orderId").asText()).isEqualTo(order.toString());
        }
    }

    private UUID insertPayment(UUID checkout, UUID owner) {
        UUID payment = UUID.randomUUID();
        assertThat(db.update("""
                insert into payment_service.payments
                    (payment_id, checkout_session_id, customer_id, amount_vnd, idempotency_key, expires_at, payment_url)
                values (?, ?, ?, 50000, ?, ?, ?)
                """, payment, checkout, owner, payment.toString(), Timestamp.from(EXPIRES_AT), PAYMENT_URL))
                .isEqualTo(1);
        return payment;
    }

    private void closeCheckout(UUID checkout) {
        assertThat(db.update("""
                insert into payment_service.payment_checkout_guard (checkout_session_id, closed_at)
                values (?, ?)
                """, checkout, Timestamp.from(NOW))).isEqualTo(1);
    }

    private void markSuccessful(UUID payment) {
        assertThat(db.update("""
                update payment_service.payments set status = 'SUCCESS', completed_at = ?
                where payment_id = ?
                """, Timestamp.from(NOW), payment)).isEqualTo(1);
    }

    private HttpResponse<String> get(String path, String... serviceTokens) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        for (String token : serviceTokens) {
            request.header("X-Service-Token", token);
        }
        return send(request.build());
    }

    private static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private static JsonNode data(HttpResponse<String> response) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.has("data")).isTrue();
        return body.path("data");
    }

    private static void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.has("data")).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo(code);
        assertThat(UUID.fromString(body.path("error").path("requestId").asText())).isNotNull();
    }

    private void assertSourceUnavailable(String path, String table) throws Exception {
        db.execute("alter table payment_service." + table + " rename to " + table + "_unavailable_phase5");
        try {
            assertError(get(path, ORDER_TOKEN), 503, "SERVICE_UNAVAILABLE");
        } finally {
            db.execute("alter table payment_service." + table + "_unavailable_phase5 rename to " + table);
        }
    }
}
