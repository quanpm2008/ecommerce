package com.example.payment_service;

import com.example.payment_service.entity.Refund;
import com.example.payment_service.entity.RefundReason;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentTotalsPostgresTests extends PaymentPhase4Fixture {
    @Test
    void addsSuccessOnceAndSeparatesRefundLiabilitiesByScope() throws Exception {
        var before = json(total("phase4-finance")).path("data");
        var first = payment(UUID.randomUUID(), UUID.randomUUID());
        first.markSuccess("captured-" + first.getPaymentId(), NOW);
        payments.saveAndFlush(first);
        refunds.saveAndFlush(Refund.createFullCheckoutRefund(first.getPaymentId(), 20_000L, "full:" + first.getPaymentId()));
        var second = payment(UUID.randomUUID(), UUID.randomUUID());
        second.markSuccess("captured-" + second.getPaymentId(), NOW);
        payments.saveAndFlush(second);
        refunds.saveAndFlush(Refund.createOrderRefund(second.getPaymentId(), UUID.randomUUID(), 10_000L,
                RefundReason.SELLER_CANCEL, "order:" + second.getPaymentId()));
        var response = total("phase4-finance");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var after = json(response).path("data");
        assertThat(after.path("capturedVnd").asLong()).isEqualTo(before.path("capturedVnd").asLong() + 40_000);
        assertThat(after.path("unsettledRefundVnd").asLong()).isEqualTo(before.path("unsettledRefundVnd").asLong() + 30_000);
        assertThat(after.path("unsettledFullCheckoutRefundVnd").asLong())
                .isEqualTo(before.path("unsettledFullCheckoutRefundVnd").asLong() + 20_000);
        assertThat(total("phase4-order").statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> total(String credential) throws Exception {
        return send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/v1/payments/totals"))
                .header("X-Service-Token", credential).GET().build());
    }
}
