package com.example.order_service;

import com.example.order_service.client.PaymentCheckoutClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountObligationsPaymentHttpTests extends PayHttpTestSupport {
    @Autowired
    PaymentCheckoutClient payments;

    @Test
    void readsPaymentOwnerAmountAndCurrencyThroughFeign() {
        UUID checkout = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        PAYMENT_READS.put(checkout, new PaymentReply(200, Map.of("data", Map.of(
                "checkoutSessionId", checkout, "customerId", customer, "paymentId", payment,
                "status", "PENDING", "amountVnd", 2500, "currency", "VND", "isSimulated", true))));

        var payload = JSON.valueToTree(payments.read(checkout, "test-order-to-payment").data());

        assertThat(payload.path("customerId").asString()).isEqualTo(customer.toString());
        assertThat(payload.path("amountVnd").asLong()).isEqualTo(2500L);
        assertThat(payload.path("currency").asString()).isEqualTo("VND");
    }
}
