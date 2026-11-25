package com.example.order_service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderReadPostgresTests extends PayHttpTestSupport {
    @Test
    void filtersOwnerBeforePagingAndKeepsAdminIdentitySeparate() throws Exception {
        var own = sample(5, 1);
        var other = sample(5, 1);
        var first = pay(own.customer(), UUID.randomUUID(), quote(own), own.address());
        var second = pay(other.customer(), UUID.randomUUID(), quote(other), other.address());
        assertThat(first.statusCode()).isEqualTo(202);
        assertThat(second.statusCode()).isEqualTo(202);
        var row = body(first).path("data").path("orders").get(0);
        String id = row.path("orderId").asString();
        var list = read(own.customer(), "/api/v1/orders/me?page=1&size=1");
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(body(list).path("meta").path("totalItems").asInt()).isOne();
        assertThat(body(list).path("data").get(0).path("orderId").asString()).isEqualTo(id);
        assertThat(read(other.customer(), "/api/v1/orders/me/" + id).statusCode()).isEqualTo(404);
        assertThat(body(read(SELLER, "/api/v1/orders/seller/orders?q=" + id)).path("data").size()).isZero();
        jdbc.update("update orders.purchase_order set status='PAID', paid_at=statement_timestamp() where order_id=?",
                UUID.fromString(id));
        assertThat(body(read(SELLER, "/api/v1/orders/seller/orders?q=" + id)).path("data").size()).isOne();
        assertThat(read(SELLER, "/api/v1/orders/admin/orders?orderId=" + id).statusCode()).isEqualTo(403);
        var admin = read(ADMIN, "/api/v1/orders/admin/orders?orderId=" + id);
        assertThat(admin.statusCode()).isEqualTo(200);
        assertThat(body(admin).path("data").get(0).path("customerName").asString()).isEqualTo("Buyer Profile");
        var detail = body(read(ADMIN, "/api/v1/orders/admin/orders/" + id)).path("data");
        assertThat(detail.path("customer").path("customerId").asString()).isEqualTo(own.customer().toString());
        assertThat(detail.path("customer").path("email").asString()).isEqualTo("buyer@example.test");
    }

    private HttpResponse<String> read(UUID user, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token(user)).GET().build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
