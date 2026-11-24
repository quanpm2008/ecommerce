package com.example.identity_service.internal;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import feign.FeignException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@Service
public class SellerObligations {
    private final Orders orders;
    private final Finance finance;
    private final String orderToken;
    private final String financeToken;

    SellerObligations(Orders orders, Finance finance,
            @Value("${identity.order.service-token:}") String orderToken,
            @Value("${identity.finance.service-token:}") String financeToken) {
        this.orders = orders;
        this.finance = finance;
        this.orderToken = orderToken;
        this.financeToken = financeToken;
    }

    Access read(UUID seller) {
        if (orderToken.isBlank() || financeToken.isBlank()) throw unavailable();
        try {
            var order = orders.current(seller, orderToken);
            var money = finance.current(seller, financeToken);
            if (order == null || order.data() == null || order.data().hasOpenOrders() == null
                    || order.data().evaluatedAt() == null || money == null || money.data() == null
                    || money.data().evaluatedAt() == null || money.data().hasPendingFinance() == null
                    || money.data().pendingBalanceVnd() == null || money.data().availableBalanceVnd() == null
                    || money.data().pendingBalanceVnd() < 0 || money.data().availableBalanceVnd() < 0) throw unavailable();
            boolean open = order.data().hasOpenOrders();
            boolean available = money.data().availableBalanceVnd() > 0;
            if (open && available) return new Access("ORDERS_AND_WITHDRAWAL", Set.of("order:fulfill:own", "wallet:read:own", "withdrawal:create:own"));
            if (open) return new Access("OPEN_ORDERS_ONLY", Set.of("order:fulfill:own", "wallet:read:own"));
            if (available) return new Access("WITHDRAWAL_ONLY", Set.of("wallet:read:own", "withdrawal:create:own"));
            var allowed = money.data().hasPendingFinance() || money.data().pendingBalanceVnd() > 0
                    ? Set.of("wallet:read:own") : Set.<String>of();
            return new Access("OBLIGATIONS_READ_ONLY", allowed);
        } catch (FeignException failure) {
            throw unavailable();
        }
    }

    private static ApiException unavailable() { return new ApiException(ErrorCode.SERVICE_UNAVAILABLE); }

    record Access(String mode, Set<String> allowed) {}
    public record Envelope<T>(T data) {}
    public record OrderSnapshot(Boolean hasOpenOrders, Instant evaluatedAt) {}
    public record FinanceSnapshot(Boolean hasPendingFinance, Long pendingBalanceVnd, Long availableBalanceVnd, Instant evaluatedAt) {}

    @FeignClient(name = "order-service", contextId = "identitySellerOrderObligations", url = "${identity.order.base-url}")
    public interface Orders {
        @GetMapping("/internal/v1/orders/sellers/{sellerId}/open-orders")
        Envelope<OrderSnapshot> current(@PathVariable("sellerId") UUID sellerId,
                @RequestHeader("X-Service-Token") String token);
    }

    @FeignClient(name = "finance-service", contextId = "identitySellerFinanceObligations",
            url = "${identity.finance.base-url:http://localhost:8082}")
    public interface Finance {
        @GetMapping("/internal/v1/finance/sellers/{sellerId}/obligations")
        Envelope<FinanceSnapshot> current(@PathVariable("sellerId") UUID sellerId, @RequestHeader("X-Service-Token") String token);
    }
}
