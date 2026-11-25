package com.example.order_service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "order.internal.tokens.finance=test-finance-to-order")
class OrderReconciliationSourceTests extends PayHttpTestSupport {
    @Test
    void financeAloneReadsAppliedCheckoutTotals() throws Exception {
        var before = body(totals("test-finance-to-order")).path("data");
        var candidate = sample(4, 2);
        var response = pay(candidate.customer(), UUID.randomUUID(), quote(candidate), candidate.address());
        assertThat(response.statusCode()).isEqualTo(202);
        UUID checkout = UUID.fromString(body(response).path("data").path("checkoutSessionId").asString());
        jdbc.update("update orders.checkout_session set status='PAID', paid_at=statement_timestamp(), payment_id=? where checkout_session_id=?",
                UUID.randomUUID(), checkout);
        var after = totals("test-finance-to-order");
        assertThat(after.statusCode()).isEqualTo(200);
        var snapshot = body(after).path("data");
        assertThat(snapshot.path("paidCheckoutCount").asLong()).isEqualTo(before.path("paidCheckoutCount").asLong() + 1);
        assertThat(snapshot.path("paidCheckoutTotalVnd").asLong()).isEqualTo(before.path("paidCheckoutTotalVnd").asLong() + 2_000);
        assertThat(snapshot.path("readAt").asString()).isNotBlank();
        assertThat(totals("test-payment-to-order").statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> totals(String credential) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/internal/v1/orders/reconciliation-totals")).header("X-Service-Token", credential).GET().build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
