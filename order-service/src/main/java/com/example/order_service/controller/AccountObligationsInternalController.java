package com.example.order_service.controller;

import com.example.order_service.exception.OrderApiExceptionHandler;
import com.example.order_service.service.AccountObligationsService;
import com.example.order_service.service.AccountObligationsService.AccountType;
import com.example.order_service.service.AccountObligationsService.Obligations;
import com.example.order_service.service.AccountObligationsService.SourceUnavailable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
class AccountObligationsInternalController {
    private final AccountObligationsService obligations;
    private final OrderApiExceptionHandler errors;
    private final ObjectWriter compactJson;

    AccountObligationsInternalController(AccountObligationsService obligations, OrderApiExceptionHandler errors,
            JsonMapper json) {
        this.obligations = obligations;
        this.errors = errors;
        this.compactJson = json.writer().without(SerializationFeature.INDENT_OUTPUT);
    }

    @GetMapping("/internal/v1/orders/accounts/{userId}/obligations")
    Map<String, Snapshot> read(@PathVariable UUID userId, @RequestParam AccountType accountType) {
        Obligations result = obligations.read(userId, accountType);
        return Map.of("data", new Snapshot(result.hasOpenOrders(), result.openOrderIds(), result.hasPendingPayment(),
                result.hasUnresolvedRefund(), Instant.now(), version(userId, accountType, result)));
    }

    private String version(UUID userId, AccountType accountType, Obligations result) {
        byte[] canonical = compactJson.writeValueAsBytes(List.of(accountType.name(), userId.toString(), result.hasOpenOrders(),
                result.openOrderIds().stream().map(UUID::toString).toList(), result.hasPendingPayment(), result.hasUnresolvedRefund()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    @ExceptionHandler(SourceUnavailable.class)
    void unavailable(HttpServletRequest request, HttpServletResponse response) throws IOException {
        errors.write(request, response, 503, "SERVICE_UNAVAILABLE", "Chưa thể xác nhận nghĩa vụ tài khoản");
    }

    record Snapshot(boolean hasOpenOrders, List<UUID> openOrderIds, boolean hasPendingPayment,
            boolean hasUnresolvedRefund, Instant evaluatedAt, String orderStateVersion) {}
}
