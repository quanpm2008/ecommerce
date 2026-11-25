package com.example.payment_service.controller;

import com.example.payment_service.security.RefundCustomerAuthorization;
import com.example.payment_service.security.RefundCustomerAuthorization.Rejected;
import com.example.payment_service.service.CustomerRefundService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/payments/refunds")
class CustomerRefundController {
    private final RefundCustomerAuthorization authorization;
    private final CustomerRefundService refunds;

    CustomerRefundController(RefundCustomerAuthorization authorization, CustomerRefundService refunds) {
        this.authorization = authorization;
        this.refunds = refunds;
    }

    @GetMapping
    Map<String, Object> list(@RequestParam UUID checkoutSessionId, HttpServletRequest request) {
        var actor = authorization.authorize(singleHeader(request, "Authorization"), RefundCustomerAuthorization.READ);
        var data = refunds.list(checkoutSessionId, actor);
        return Map.of("data", data, "meta", Map.of("page", 1, "size", data.size(),
                "totalItems", data.size(), "totalPages", data.isEmpty() ? 0 : 1));
    }

    @PostMapping("/{refundId}/retry")
    ResponseEntity<?> retry(@PathVariable UUID refundId, @RequestBody JsonNode body, HttpServletRequest request) {
        var actor = authorization.authorize(singleHeader(request, "Authorization"), RefundCustomerAuthorization.RETRY);
        String header = singleHeader(request, "Idempotency-Key");
        if (header == null || header.isBlank()) {
            throw new Rejected(400, "IDEMPOTENCY_KEY_REQUIRED");
        }
        UUID key;
        try {
            key = UUID.fromString(header);
            if (!key.toString().equalsIgnoreCase(header)) {
                throw new IllegalArgumentException("Invalid key");
            }
        } catch (IllegalArgumentException invalid) {
            throw new Rejected(400, "VALIDATION_ERROR");
        }
        var cycle = body.path("expectedRetryCycle");
        if (!body.isObject() || body.size() != 1 || !cycle.isIntegralNumber() || !cycle.canConvertToInt() || cycle.asInt() < 0) {
            throw new Rejected(400, "VALIDATION_ERROR");
        }
        return ResponseEntity.accepted().body(Map.of("data", refunds.retry(refundId, key, cycle.asInt(), actor)));
    }

    @ExceptionHandler(Rejected.class)
    ResponseEntity<?> rejected(Rejected failure, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        return ResponseEntity.status(failure.status()).header("X-Request-Id", requestId)
                .body(Map.of("error", Map.of("code", failure.getMessage(), "message", "Yêu cầu không thể thực hiện",
                        "details", List.of(), "requestId", requestId)));
    }

    private static String singleHeader(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        return values.size() == 1 ? values.getFirst() : null;
    }

}
