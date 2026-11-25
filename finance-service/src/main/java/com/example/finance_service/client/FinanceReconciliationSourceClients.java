package com.example.finance_service.client;

import java.time.Instant;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

public final class FinanceReconciliationSourceClients {
    private FinanceReconciliationSourceClients() {}

    @FeignClient(name = "order-service", contextId = "financeOrderTotals", url = "${finance.order.base-url:http://localhost:8085}")
    public interface Orders {
        @GetMapping("/internal/v1/orders/reconciliation-totals")
        Envelope<OrderTotals> totals(@RequestHeader("X-Service-Token") String token);
    }

    @FeignClient(name = "payment-service", contextId = "financePaymentTotals", url = "${finance.payment.base-url:http://localhost:8086}")
    public interface Payments {
        @GetMapping("/internal/v1/payments/totals")
        Envelope<PaymentTotals> totals(@RequestHeader("X-Service-Token") String token);
    }

    public record Envelope<T>(T data) {}
    public record OrderTotals(Long paidCheckoutCount, Long paidCheckoutTotalVnd, Instant readAt) {}
    public record PaymentTotals(Long capturedVnd, Long refundedVnd, Long unsettledRefundVnd,
                                Long unsettledFullCheckoutRefundVnd, Instant readAt) {}
}
