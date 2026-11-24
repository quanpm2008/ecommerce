package com.example.payment_service.client;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "order-service", contextId = "orderCollectionReview", url = "${payment.order.base-url:http://localhost:8085}")
public interface OrderCollectionReviewClient {
    @GetMapping("/internal/v1/orders/collection-reviews")
    Reviews reviews(@RequestHeader("X-Service-Token") String credential, @RequestHeader("Authorization") String bearer,
            @RequestParam("checkoutSessionId") UUID checkoutId, @RequestParam("page") int page, @RequestParam("size") int size);

    @PostMapping("/internal/v1/orders/checkout-sessions/{id}/mark-handled")
    Handled mark(@RequestHeader("X-Service-Token") String credential, @RequestHeader("Authorization") String bearer,
            @RequestHeader("X-Request-Id") String requestId, @PathVariable("id") UUID checkoutId, @RequestBody Note note);

    record Reviews(Page data) {}
    record Page(List<Review> items, long totalItems) {}
    record Review(UUID checkoutSessionId, UUID paymentId, long amountVnd, Instant since, boolean needsManualReview,
                  String manualNote) {}
    record Handled(Result data) {}
    record Result(UUID checkoutSessionId, String manualNote, Instant handledAt) {}
    record Note(String note) {}
}
