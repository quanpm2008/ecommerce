package com.example.payment_service.client;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "order-service", contextId = "orderCheckoutPayable",
        url = "${payment.order.base-url:http://localhost:8085}")
public interface OrderCheckoutPayableClient {
    @GetMapping("/internal/v1/orders/checkout-sessions/{id}/payable")
    Envelope payable(@PathVariable("id") UUID checkoutId, @RequestHeader("X-Service-Token") String credential);

    record Envelope(Payable data) { }

    record Payable(UUID customerId, String status, Instant expiresAt, Long totalAmountVnd, List<Order> orders) { }

    record Order(UUID orderId, String status) {
        public Order(UUID orderId) {
            this(orderId, null);
        }
    }
}
