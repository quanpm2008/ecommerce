package com.example.payment_service.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.payment_service.service.PaymentCollectionService;
import com.example.payment_service.service.PaymentCollectionService.CheckoutConflict;
import com.example.payment_service.service.PaymentCollectionService.CheckoutNotFound;
import com.example.payment_service.service.PaymentCollectionService.SourceUnavailable;
import com.example.payment_service.service.RefundReadService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/internal/v1/payments")
public class CheckoutPaymentInternalController {
    private final PaymentCollectionService collections;
    private final RefundReadService refunds;
    private final byte[] orderToken;

    public CheckoutPaymentInternalController(PaymentCollectionService collections, RefundReadService refunds,
            @Value("${payment.internal.order-token:}") String orderToken) {
        this.collections = collections;
        this.refunds = refunds;
        this.orderToken = orderToken.isBlank() ? new byte[0] : orderToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/checkouts/{checkoutSessionId}/provider-session")
    public ResponseEntity<?> create(@PathVariable UUID checkoutSessionId, HttpServletRequest request) {
        return authorized(request, () -> ResponseEntity.ok(new Envelope(collections.createOrGet(checkoutSessionId))));
    }

    @GetMapping("/by-checkout/{checkoutSessionId}")
    public ResponseEntity<?> read(@PathVariable UUID checkoutSessionId, HttpServletRequest request) {
        return authorized(request, () -> ResponseEntity.ok(new Envelope(collections.readWithOwner(checkoutSessionId))));
    }

    @GetMapping("/refunds")
    public ResponseEntity<?> refunds(@RequestParam(name = "orderIds", required = false) List<UUID> orderIds,
            @RequestParam(name = "checkoutSessionIds", required = false) List<UUID> checkoutIds, HttpServletRequest request) {
        return authorized(request, () -> {
            var orders = orderIds == null ? List.<UUID>of() : orderIds;
            var checkouts = checkoutIds == null ? List.<UUID>of() : checkoutIds;
            if (orders.isEmpty() == checkouts.isEmpty()) {
                return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Specify either orderIds or checkoutSessionIds");
            }
            return ResponseEntity.ok(new Envelope(refunds.lookup(orders, checkouts)));
        });
    }

    private ResponseEntity<?> authorized(HttpServletRequest request, Supplier<ResponseEntity<?>> action) {
        var supplied = Collections.list(request.getHeaders("X-Service-Token"));
        if (orderToken.length == 0 || supplied.size() != 1 || !MessageDigest.isEqual(
                orderToken, supplied.getFirst().getBytes(StandardCharsets.UTF_8))) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Order service credential required");
        }
        try {
            return action.get();
        } catch (CheckoutNotFound missing) {
            return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Checkout not found");
        } catch (CheckoutConflict conflict) {
            return error(HttpStatus.CONFLICT, "CONFLICT", "Checkout snapshot conflicts with the existing payment");
        } catch (SourceUnavailable | DataAccessException unavailable) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Payment collection is temporarily unavailable");
        }
    }

    private static ResponseEntity<Map<String, ApiError>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(Map.of("error", new ApiError(code, message, Map.of(), UUID.randomUUID())));
    }

    public record Envelope(Object data) { }

    private record ApiError(String code, String message, Map<String, Object> details, UUID requestId) { }
}
