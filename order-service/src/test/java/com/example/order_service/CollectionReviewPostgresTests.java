package com.example.order_service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CollectionReviewPostgresTests extends PayHttpTestSupport {
    @Test
    void locksNoteAndAuditInOneTransactionAndChecksTwoCredentials() throws Exception {
        var sample = sample(5, 1);
        var response = pay(sample.customer(), UUID.randomUUID(), quote(sample), sample.address());
        assertThat(response.statusCode()).isEqualTo(202);
        UUID checkout = UUID.fromString(body(response).path("data").path("checkoutSessionId").asString());
        jdbc.update("update orders.checkout_session set needs_manual_review=true where checkout_session_id=?", checkout);
        assertThat(mark(checkout, ADMIN, "wrong", "reviewed").statusCode()).isEqualTo(401);
        assertThat(mark(checkout, READ_ADMIN, "test-payment-to-order", "reviewed").statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from orders.admin_audit_log where target_id=?", Integer.class, checkout)).isZero();
        jdbc.execute("alter table orders.admin_audit_log add constraint phase4_audit_rollback check (reason <> 'force rollback')");
        try {
            assertThat(mark(checkout, ADMIN, "test-payment-to-order", "force rollback").statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject("select needs_manual_review from orders.checkout_session where checkout_session_id=?",
                    Boolean.class, checkout)).isTrue();
        } finally {
            jdbc.execute("alter table orders.admin_audit_log drop constraint phase4_audit_rollback");
        }
        assertThat(mark(checkout, ADMIN, "test-payment-to-order", "reviewed").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select manual_note from orders.checkout_session where checkout_session_id=?", String.class,
                checkout)).isEqualTo("reviewed");
        assertThat(jdbc.queryForObject("select actor_user_id from orders.admin_audit_log where target_id=?", UUID.class, checkout))
                .isEqualTo(ADMIN);
        assertThat(jdbc.queryForObject("select status from orders.checkout_session where checkout_session_id=?", String.class, checkout))
                .isEqualTo("AWAITING_PAYMENT");
    }

    private HttpResponse<String> mark(UUID checkout, UUID user, String serviceToken, String note) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/internal/v1/orders/checkout-sessions/" + checkout + "/mark-handled"))
                .header("Authorization", "Bearer " + token(user)).header("X-Service-Token", serviceToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(java.util.Map.of("note", note)))).build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
