package com.example.finance_service.controller;

import com.example.finance_service.entity.Settlement;
import com.example.finance_service.entity.WalletTransaction;
import com.example.finance_service.security.FinanceAuthorization;
import com.example.finance_service.security.FinanceAuthorization.Rejected;
import com.example.finance_service.service.SellerFinanceReadService;
import com.example.finance_service.service.SellerFinanceReadService.Dates;
import com.example.finance_service.service.WithdrawalService;
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
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/finance/seller")
class SellerFinanceController {
    private final FinanceAuthorization auth;
    private final SellerFinanceReadService reads;
    private final WithdrawalService withdrawals;

    SellerFinanceController(FinanceAuthorization auth, SellerFinanceReadService reads, WithdrawalService withdrawals) {
        this.auth = auth;
        this.reads = reads;
        this.withdrawals = withdrawals;
    }

    @GetMapping("/wallet")
    Map<String, Object> wallet(HttpServletRequest request) {
        return Map.of("data", reads.wallet(seller(request)));
    }

    @GetMapping("/wallet/transactions")
    Map<String, Object> ledger(@RequestParam(required = false) WalletTransaction.TransactionType type,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        return reads.transactions(seller(request), type, new Dates(from, to), page, size);
    }

    @GetMapping("/settlements")
    Map<String, Object> settlements(@RequestParam(required = false) List<Settlement.Status> status,
            @RequestParam(required = false) UUID orderId, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        var actor = seller(request);
        if (actor.withdrawalOnly()) throw new Rejected(403, "FORBIDDEN");
        return reads.settlements(actor, orderId, status, new Dates(from, to), page, size);
    }

    @PostMapping("/withdrawals")
    ResponseEntity<?> withdraw(@RequestBody JsonNode body, HttpServletRequest request) {
        var actor = auth.seller(header(request, "Authorization"), FinanceAuthorization.WITHDRAW);
        String key = header(request, "Idempotency-Key");
        if (key == null || key.isBlank()) throw new Rejected(400, "IDEMPOTENCY_KEY_REQUIRED");
        try {
            var parsed = UUID.fromString(key);
            if (!parsed.toString().equalsIgnoreCase(key)) throw new IllegalArgumentException("Invalid key");
            key = parsed.toString();
        } catch (IllegalArgumentException invalid) {
            throw new Rejected(400, "VALIDATION_ERROR");
        }
        var amount = body.path("amountVnd");
        if (!body.isObject() || body.size() != 1 || !amount.isIntegralNumber()
                || !amount.canConvertToLong() || amount.asLong() <= 0) throw new Rejected(400, "VALIDATION_ERROR");
        var result = withdrawals.withdraw(actor, key, amount.asLong());
        var response = ResponseEntity.status(result.replay() ? 200 : 201);
        if (result.replay()) response.header("Idempotent-Replayed", "true");
        return response.body(Map.of("data", SellerFinanceReadService.receipt(result.withdrawal())));
    }

    @GetMapping("/withdrawals")
    Map<String, Object> withdrawals(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        var actor = seller(request);
        if (actor.readOnly()) throw new Rejected(403, "FORBIDDEN");
        return reads.withdrawals(actor.id(), new Dates(from, to), page, size);
    }

    @GetMapping("/withdrawals/{id}")
    Map<String, Object> receipt(@PathVariable UUID id, HttpServletRequest request) {
        var actor = seller(request);
        if (actor.readOnly()) throw new Rejected(403, "FORBIDDEN");
        return Map.of("data", reads.receipt(actor.id(), id));
    }

    private FinanceAuthorization.SellerActor seller(HttpServletRequest request) {
        return auth.seller(header(request, "Authorization"), FinanceAuthorization.READ);
    }

    static String header(HttpServletRequest request, String name) {
        var headers = Collections.list(request.getHeaders(name));
        return headers.size() == 1 ? headers.getFirst() : null;
    }

    @ExceptionHandler(Rejected.class)
    ResponseEntity<?> rejected(Rejected failure, HttpServletRequest request) {
        return error(failure.status(), failure.getMessage(), request);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable(DataAccessException failure, HttpServletRequest request) {
        return error(503, "SERVICE_UNAVAILABLE", request);
    }

    static ResponseEntity<?> error(int status, String code, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
        return ResponseEntity.status(status).header("X-Request-Id", requestId)
                .body(Map.of("error", Map.of("code", code, "message", code, "details", List.of(), "requestId", requestId)));
    }
}
