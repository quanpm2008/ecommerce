package com.example.payment_service.controller;

import com.example.payment_service.entity.RefundStatus;
import com.example.payment_service.security.RefundCustomerAuthorization;
import com.example.payment_service.security.RefundCustomerAuthorization.Rejected;
import com.example.payment_service.service.PaymentIssueReadService;
import com.example.payment_service.service.PaymentReadService;
import com.example.payment_service.service.CollectionReviewClientService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
class PaymentReadController {
    static final String ADMIN_READ = "operations:read:any";
    private final RefundCustomerAuthorization auth;
    private final PaymentReadService reads;
    private final PaymentIssueReadService issues;
    private final CollectionReviewClientService reviews;

    PaymentReadController(RefundCustomerAuthorization auth, PaymentReadService reads, PaymentIssueReadService issues,
            CollectionReviewClientService reviews) {
        this.auth = auth;
        this.reads = reads;
        this.issues = issues;
        this.reviews = reviews;
    }

    @GetMapping("/by-checkout/{id}")
    Map<String, Object> customer(@PathVariable UUID id, HttpServletRequest request) {
        return reads.customer(id, auth.authorize(bearer(request), RefundCustomerAuthorization.READ));
    }

    @GetMapping("/admin/payments/by-checkout/{id}")
    Map<String, Object> admin(@PathVariable UUID id, HttpServletRequest request) {
        String bearer = bearer(request);
        auth.authorizeAdmin(bearer, ADMIN_READ);
        var data = reads.adminDetail(id);
        data.put("manualNote", reviews.note(id, bearer));
        return Map.of("data", data);
    }

    @GetMapping("/admin/issues")
    Map<String, Object> issues(@RequestParam(required = false) PaymentIssueReadService.Type type,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        String bearer = bearer(request);
        auth.authorizeAdmin(bearer, ADMIN_READ);
        PaymentReadService.page(page, size);
        var flags = type == PaymentIssueReadService.Type.REFUND_FAILED || type == PaymentIssueReadService.Type.REFUND_STUCK
                ? List.<com.example.payment_service.client.OrderCollectionReviewClient.Review>of() : reviews.flagged(bearer);
        return issues.issues(type, page, size, flags);
    }

    @PostMapping("/admin/checkout-sessions/{id}/mark-handled")
    Map<String, Object> mark(@PathVariable UUID id, @RequestBody Note body, HttpServletRequest request) {
        String bearer = bearer(request);
        auth.authorizeAdmin(bearer, "operations:annotate");
        String note = body.note();
        if (note == null || note.isBlank() || note.codePointCount(0, note.length()) > 1000) {
            throw new Rejected(400, "VALIDATION_ERROR");
        }
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) requestId = UUID.randomUUID().toString();
        return Map.of("data", reviews.mark(id, bearer, requestId, note));
    }

    record Note(String note) {}

    @GetMapping("/admin/refunds")
    Map<String, Object> refunds(@RequestParam(required = false) RefundStatus status,
            @RequestParam(required = false) UUID orderId, @RequestParam(required = false) UUID paymentId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        auth.authorizeAdmin(bearer(request), ADMIN_READ);
        return reads.adminRefunds(status, orderId, paymentId, page, size);
    }

    @GetMapping("/admin/summary")
    Map<String, Object> summary(@RequestParam LocalDate from, @RequestParam LocalDate to, HttpServletRequest request) {
        auth.authorizeAdmin(bearer(request), ADMIN_READ);
        return Map.of("data", reads.summary(from, to));
    }

    static String bearer(HttpServletRequest request) {
        var list = Collections.list(request.getHeaders("Authorization"));
        return list.size() == 1 ? list.getFirst() : null;
    }

    @ExceptionHandler(Rejected.class)
    ResponseEntity<?> rejected(Rejected failure, HttpServletRequest request) {
        return error(failure.status(), failure.getMessage(), request);
    }

    @ExceptionHandler({DataAccessException.class, jakarta.persistence.PersistenceException.class})
    ResponseEntity<?> unavailable(HttpServletRequest request) {
        return error(503, "SERVICE_UNAVAILABLE", request);
    }

    static ResponseEntity<?> error(int status, String code, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
        return ResponseEntity.status(status).header("X-Request-Id", requestId)
                .body(Map.of("error", Map.of("code", code, "message", code, "details", List.of(), "requestId", requestId)));
    }
}
