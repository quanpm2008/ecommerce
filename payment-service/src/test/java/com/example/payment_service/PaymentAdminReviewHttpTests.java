package com.example.payment_service;

import com.example.payment_service.client.OrderCollectionReviewClient;
import com.example.payment_service.security.RefundCustomerAuthorization.Rejected;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentAdminReviewHttpTests extends PaymentPhase4Fixture {
    @Test
    void requiresAnnotationPermissionAndForwardsOnlyTheCollectionCommand() throws Exception {
        UUID checkout = UUID.randomUUID();
        String path = "http://127.0.0.1:" + port + "/api/v1/payments/admin/checkout-sessions/" + checkout + "/mark-handled";
        var request = HttpRequest.newBuilder(URI.create(path)).header("Authorization", "Bearer operations")
                .header("X-Request-Id", "phase4-review").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"note\":\"checked\"}")).build();
        doThrow(new Rejected(403, "FORBIDDEN")).when(auth).authorizeAdmin("Bearer operations", "operations:annotate");
        assertThat(send(request).statusCode()).isEqualTo(403);
        verifyNoInteractions(reviews);
        org.mockito.Mockito.doNothing().when(auth).authorizeAdmin("Bearer operations", "operations:annotate");
        when(reviews.mark("phase4-payment", "Bearer operations", "phase4-review", checkout,
                new OrderCollectionReviewClient.Note("checked")))
                .thenReturn(new OrderCollectionReviewClient.Handled(new OrderCollectionReviewClient.Result(checkout, "checked", NOW)));
        var response = send(request);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(json(response).path("data").path("manualNote").asString()).isEqualTo("checked");
    }
}
