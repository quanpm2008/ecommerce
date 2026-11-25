package com.example.order_service.controller;

import com.example.order_service.entity.Order;
import com.example.order_service.repository.CheckoutOrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
class SellerObligationsInternalController {
    private final CheckoutOrderRepository orders;

    SellerObligationsInternalController(CheckoutOrderRepository orders) { this.orders = orders; }

    @GetMapping("/internal/v1/orders/sellers/{id}/open-orders")
    Map<String, Snapshot> obligations(@PathVariable UUID id) {
        boolean open = orders.existsBySellerIdAndStatusIn(id,
                List.of(Order.Status.PAID, Order.Status.PACKING, Order.Status.DELIVERED));
        return Map.of("data", new Snapshot(open, Instant.now()));
    }

    record Snapshot(boolean hasOpenOrders, Instant evaluatedAt) {}
}
