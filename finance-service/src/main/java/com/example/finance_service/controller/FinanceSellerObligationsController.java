package com.example.finance_service.controller;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
class FinanceSellerObligationsController {
    private final EntityManager entities;
    private final byte[] token;

    FinanceSellerObligationsController(EntityManager entities,
            @Value("${finance.internal.tokens.identity:}") String credential,
            @Value("${finance.internal.tokens.order:}") String orderCredential) {
        if (!credential.isBlank() && credential.equals(orderCredential)) {
            throw new IllegalStateException("Finance service tokens must be distinct per caller");
        }
        this.entities = entities;
        this.token = credential.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/internal/v1/finance/sellers/{sellerId}/obligations")
    @Transactional(readOnly = true)
    ResponseEntity<?> obligations(@PathVariable UUID sellerId, HttpServletRequest request) {
        var supplied = Collections.list(request.getHeaders("X-Service-Token"));
        if (token.length == 0 || supplied.size() != 1
                || !MessageDigest.isEqual(token, supplied.getFirst().getBytes(StandardCharsets.UTF_8))) {
            return SellerFinanceController.error(401, "UNAUTHENTICATED", request);
        }
        var list = entities.createNativeQuery("""
                select w.pending_balance_vnd,w.available_balance_vnd,w.row_version,
                  w.pending_balance_vnd > 0
                  or exists(select 1 from finance.settlement s where s.seller_id=:seller and s.status in ('PENDING','PENDING_REFUND'))
                  or exists(select 1 from finance.settlement_inbox i
                    left join finance.settlement s on cast(s.order_id as text)=i.payload #>> '{payload,orderId}'
                    where i.status='WAITING_DEPENDENCY' and
                    (s.seller_id=:seller or i.payload #>> '{payload,sellerId}'=cast(:seller as text)
                      or exists (select 1 from jsonb_array_elements(
                         case when jsonb_typeof(i.payload #> '{payload,orders}')='array'
                              then i.payload #> '{payload,orders}' else cast('[]' as jsonb) end) e
                         where e->>'sellerId'=cast(:seller as text))))
                from finance.wallet w where w.owner_type='SELLER' and w.currency='VND' and w.owner_id=:seller
                """).setParameter("seller", sellerId).getResultList();
        if (list.isEmpty()) return SellerFinanceController.error(404, "NOT_FOUND", request);
        var row = (Object[]) list.getFirst();
        return ResponseEntity.ok(Map.of("data", new Snapshot((Boolean) row[3], ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(), Instant.now(), ((Number) row[2]).longValue())));
    }

    record Snapshot(boolean hasPendingFinance, long pendingBalanceVnd, long availableBalanceVnd,
                    Instant evaluatedAt, long financeStateVersion) {}
}
