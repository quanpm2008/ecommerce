package com.example.payment_service.controller;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PaymentTotalsInternalController {
    private final EntityManager entities;
    private final byte[] credential;

    PaymentTotalsInternalController(EntityManager entities, @Value("${payment.internal.finance-token:}") String credential,
            @Value("${payment.internal.order-token:}") String orderToken) {
        if (!credential.isBlank() && credential.equals(orderToken)) {
            throw new IllegalStateException("Payment internal credentials must be distinct");
        }
        this.entities = entities;
        this.credential = credential.isBlank() ? new byte[0] : credential.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/internal/v1/payments/totals")
    @Transactional(readOnly = true)
    ResponseEntity<?> totals(HttpServletRequest request) {
        var supplied = Collections.list(request.getHeaders("X-Service-Token"));
        if (credential.length == 0 || supplied.size() != 1
                || !MessageDigest.isEqual(credential, supplied.getFirst().getBytes(StandardCharsets.UTF_8))) {
            return PaymentReadController.error(401, "UNAUTHENTICATED", request);
        }
        var sums = (Object[]) entities.createNativeQuery("""
                select (select coalesce(sum(amount_vnd),0) from payment_service.payments where status='SUCCESS'),
                    coalesce(sum(amount_vnd) filter (where status='SUCCESS'),0),
                    coalesce(sum(amount_vnd) filter (where status in ('PENDING','FAILED')),0),
                    coalesce(sum(amount_vnd) filter (where status in ('PENDING','FAILED') and scope='FULL_CHECKOUT'),0),
                    statement_timestamp() from payment_service.refunds
                """).getSingleResult();
        Instant at = sums[4] instanceof OffsetDateTime timestamp ? timestamp.toInstant() : (Instant) sums[4];
        return ResponseEntity.ok(Map.of("data", new Totals(amount(sums[0]), amount(sums[1]), amount(sums[2]), amount(sums[3]), at)));
    }

    private static long amount(Object value) { return new java.math.BigDecimal(value.toString()).longValueExact(); }

    @ExceptionHandler({DataAccessException.class, jakarta.persistence.PersistenceException.class, ArithmeticException.class})
    ResponseEntity<?> unavailable(HttpServletRequest request) {
        return PaymentReadController.error(503, "SERVICE_UNAVAILABLE", request);
    }

    record Totals(long capturedVnd, long refundedVnd, long unsettledRefundVnd,
                  long unsettledFullCheckoutRefundVnd, Instant readAt) {}
}
