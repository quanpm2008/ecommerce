package com.example.payment_service;

import com.example.payment_service.client.OrderCheckoutPayableClient;
import com.example.payment_service.client.OrderCollectionReviewClient;
import com.example.payment_service.entity.Payment;
import com.example.payment_service.entity.Refund;
import com.example.payment_service.entity.RefundAttempt;
import com.example.payment_service.repository.PaymentRepository;
import com.example.payment_service.repository.RefundRepository;
import com.example.payment_service.repository.RefundAttemptRepository;
import com.example.payment_service.security.RefundCustomerAuthorization;
import com.example.payment_service.security.RefundCustomerAuthorization.Actor;
import com.example.payment_service.security.RefundCustomerAuthorization.Rejected;
import com.example.payment_service.service.PaymentIssueReadService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class PaymentReadPostgresTests extends PaymentPhase4Fixture {
    @Test
    void validatesOwnershipBeforeNullResponseAndHidesLinkOnClosedCheckout() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID checkout = UUID.randomUUID();
        when(auth.authorize("Bearer customer", RefundCustomerAuthorization.READ)).thenReturn(new Actor(actor, false, false));
        when(order.payable(checkout, "phase4-payment")).thenReturn(new OrderCheckoutPayableClient.Envelope(
                new OrderCheckoutPayableClient.Payable(UUID.randomUUID(), "AWAITING_PAYMENT", NOW.plusSeconds(600), 20000L, List.of())));
        assertThat(get("/api/v1/payments/by-checkout/" + checkout, "customer").statusCode()).isEqualTo(404);
        when(order.payable(checkout, "phase4-payment")).thenReturn(new OrderCheckoutPayableClient.Envelope(
                new OrderCheckoutPayableClient.Payable(actor, "AWAITING_PAYMENT", NOW.plusSeconds(600), 20000L, List.of())));
        assertThat(json(get("/api/v1/payments/by-checkout/" + checkout, "customer")).path("data").isNull()).isTrue();
        var payment = payment(checkout, actor);
        payment.setPaymentUrl("https://example.invalid/pay");
        payments.saveAndFlush(payment);
        assertThat(json(get("/api/v1/payments/by-checkout/" + checkout, "customer")).path("data")
                .path("paymentUrl").asString()).isEqualTo("https://example.invalid/pay");
        when(order.payable(checkout, "phase4-payment")).thenReturn(new OrderCheckoutPayableClient.Envelope(
                new OrderCheckoutPayableClient.Payable(actor, "CLOSED", NOW.plusSeconds(600), 20000L, List.of())));
        assertThat(json(get("/api/v1/payments/by-checkout/" + checkout, "customer")).path("data").has("paymentUrl")).isFalse();
        payment.markSuccess("phase4-" + payment.getPaymentId(), NOW);
        payments.saveAndFlush(payment);
        var id = UUID.randomUUID();
        when(auth.authorize("Bearer customer", RefundCustomerAuthorization.READ)).thenReturn(new Actor(actor, true, false));
        when(order.payable(checkout, "phase4-payment")).thenReturn(new OrderCheckoutPayableClient.Envelope(
                new OrderCheckoutPayableClient.Payable(actor, "PAID", NOW.plusSeconds(600), 20000L,
                        List.of(new OrderCheckoutPayableClient.Order(id, "CONFIRMED")))));
        assertThat(get("/api/v1/payments/by-checkout/" + checkout, "customer").statusCode()).isEqualTo(403);
        when(order.payable(checkout, "phase4-payment")).thenReturn(new OrderCheckoutPayableClient.Envelope(
                new OrderCheckoutPayableClient.Payable(actor, "PAID", NOW.plusSeconds(600), 20000L,
                        List.of(new OrderCheckoutPayableClient.Order(id, "DELIVERED")))));
        assertThat(get("/api/v1/payments/by-checkout/" + checkout, "customer").statusCode()).isEqualTo(200);
    }

    @Test
    void issuesUseInclusiveThresholdAndReviewSupersedesLinkDelay() throws Exception {
        var payment = payment(UUID.randomUUID(), UUID.randomUUID());
        var id = payment.getPaymentId();
        db.update("update payment_service.payments set created_at=? where payment_id=?", Timestamp.from(NOW.minusSeconds(119)), id);
        when(reviews.reviews("phase4-payment", "Bearer admin", null, 1, 100))
                .thenReturn(new OrderCollectionReviewClient.Reviews(new OrderCollectionReviewClient.Page(List.of(), 0)));
        var initially = list(PaymentIssueReadService.Type.PAYMENT_PENDING);
        assertThat(initially.toString()).doesNotContain(id.toString());
        db.update("update payment_service.payments set created_at=? where payment_id=?", Timestamp.from(NOW.minusSeconds(120)), id);
        assertThat(list(PaymentIssueReadService.Type.PAYMENT_PENDING).toString()).contains(id.toString(), "PAYMENT_LINK_DELAYED");
        when(reviews.reviews("phase4-payment", "Bearer admin", null, 1, 100))
                .thenReturn(new OrderCollectionReviewClient.Reviews(new OrderCollectionReviewClient.Page(List.of(
                        new OrderCollectionReviewClient.Review(payment.getCheckoutSessionId(), id, 20000,
                                NOW.minusSeconds(200), true, null)), 1)));
        assertThat(list(PaymentIssueReadService.Type.PAYMENT_PENDING).toString()).doesNotContain(id.toString());
        assertThat(list(PaymentIssueReadService.Type.MANUAL_REVIEW).toString()).contains(id.toString(), "COLLECTION_REVIEW_REQUIRED");
    }

    private JsonNode list(PaymentIssueReadService.Type type) throws Exception {
        var route = "/api/v1/payments/admin/issues?type=" + type.name() + "&size=100";
        return checkIssues(route);
    }

    @Test
    void refundAlertUsesCurrentAttemptAndKeepsReviewAheadOfExhaustion() throws Exception {
        var payment = payment(UUID.randomUUID(), UUID.randomUUID());
        var refund = refunds.saveAndFlush(Refund.createFullCheckoutRefund(payment.getPaymentId(), 20_000L,
                payment.getPaymentId().toString()));
        var id = refund.getRefundId();
        var attempt = attempts.saveAndFlush(new RefundAttempt(refund, NOW.minusSeconds(1799)));
        assertThat(list(PaymentIssueReadService.Type.REFUND_STUCK).toString()).doesNotContain(id.toString());
        db.update("update payment_service.payment_refund_attempts set created_at=? where attempt_id=?",
                Timestamp.from(NOW.minusSeconds(1800)), attempt.getAttemptId());
        assertThat(list(PaymentIssueReadService.Type.REFUND_STUCK).toString()).contains(id.toString(), "REFUND_ATTEMPT_STUCK");
        db.update("""
                update payment_service.refunds set status='FAILED',retry_count=3,customer_retry_required=true,
                  completed_at=? where refund_id=?
                """, Timestamp.from(NOW), id);
        assertThat(list(PaymentIssueReadService.Type.REFUND_FAILED).toString()).contains(id.toString(), "REFUND_WAITING_CUSTOMER");
        db.update("insert into payment_service.payment_refund_work(refund_id,review_required_at) values (?,?)",
                id, Timestamp.from(NOW));
        when(reviews.reviews("phase4-payment", "Bearer admin", null, 1, 100))
                .thenReturn(new OrderCollectionReviewClient.Reviews(new OrderCollectionReviewClient.Page(List.of(), 0)));
        assertThat(list(PaymentIssueReadService.Type.REFUND_FAILED).toString()).doesNotContain(id.toString());
        assertThat(list(PaymentIssueReadService.Type.MANUAL_REVIEW).toString()).contains(id.toString(), "REFUND_REVIEW_REQUIRED");
    }

    private JsonNode checkIssues(String route) throws Exception {
        var response = get(route, "admin");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json(response).path("data");
    }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "payment.internal.finance-token=phase4-finance", "payment.internal.order-token=phase4-order",
        "payment.order.credential=phase4-payment", "payment.collection.initial-delay=3600000",
        "payment.refund.initial-delay=3600000", "logging.level.root=WARN"
})
abstract class PaymentPhase4Fixture {
    static final Instant NOW = Instant.parse("2026-11-24T07:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PaymentRepository payments;
    @Autowired RefundRepository refunds;
    @Autowired RefundAttemptRepository attempts;
    @MockitoBean RefundCustomerAuthorization auth;
    @MockitoBean OrderCheckoutPayableClient order;
    @MockitoBean OrderCollectionReviewClient reviews;
    @org.springframework.test.context.bean.override.convention.TestBean Clock clock;

    static Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }

    Payment payment(UUID checkout, UUID owner) {
        assertThat(db.queryForObject("select current_database()", String.class)).startsWith("payment_test_");
        var payment = new Payment(checkout, owner, 20000L, "VND", "DEMO_PROVIDER", "DEV", checkout.toString());
        payment.setExpiresAt(NOW.plusSeconds(600));
        return payments.saveAndFlush(payment);
    }

    HttpResponse<String> get(String path, String actor) throws Exception {
        return send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + actor).GET().build());
    }

    HttpResponse<String> send(HttpRequest request) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    JsonNode json(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }
}
