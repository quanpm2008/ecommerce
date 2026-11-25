package com.example.order_service.controller;

import com.example.order_service.repository.CheckoutSessionJpaRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderReconciliationInternalController {
    private final CheckoutSessionJpaRepository checkouts;

    OrderReconciliationInternalController(CheckoutSessionJpaRepository checkouts) {
        this.checkouts = checkouts;
    }

    @GetMapping("/internal/v1/orders/reconciliation-totals")
    Map<String, Object> totals() {
        var sample = checkouts.paidTotals();
        return Map.of("data", new Totals(sample.getPaidCount(), sample.getPaidTotal(), sample.getReadAt()));
    }

    record Totals(long paidCheckoutCount, long paidCheckoutTotalVnd, Instant readAt) {}
}
