package com.example.finance_service.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
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
                with paid_orders as (
                    select i.status, cast(line->>'orderId' as uuid) as order_id,
                        cast(line->>'sellerId' as uuid) as seller_id,
                        cast(i.payload #>> '{payload,paymentId}' as uuid) as payment_id,
                        cast(line->>'totalAmountVnd' as bigint) as gross_vnd,
                        cast(line->>'commissionBps' as integer) as commission_bps
                    from finance.settlement_inbox i
                    cross join lateral jsonb_array_elements(
                        case when jsonb_typeof(i.payload #> '{payload,orders}') = 'array'
                            then i.payload #> '{payload,orders}' else cast('[]' as jsonb) end) as orders(line)
                    where i.event_type = 'checkout.paid' and i.payload->>'producer' = 'order-service'
                ), lifecycle_events as (
                    select i.event_type, i.status, cast(i.payload #>> '{payload,orderId}' as uuid) as order_id,
                        cast(i.payload #>> '{payload,sellerId}' as uuid) as seller_id,
                        cast(i.payload #>> '{payload,paymentId}' as uuid) as payment_id,
                        cast(i.payload #>> '{payload,amountVnd}' as bigint) as amount_vnd,
                        cast(i.payload #>> '{payload,totalAmountVnd}' as bigint) as cancelled_amount_vnd,
                        cast(i.payload #>> '{payload,refundId}' as uuid) as refund_id
                    from finance.settlement_inbox i
                    where i.status in ('WAITING_DEPENDENCY', 'FAILED_REVIEW') and (
                        (i.event_type = 'refund.succeeded' and i.payload->>'producer' = 'payment-service'
                            and i.payload #>> '{payload,scope}' = 'ORDER')
                        or (i.payload->>'producer' = 'order-service' and (
                            i.event_type = 'order.confirmed'
                            or (i.event_type = 'order.cancelled' and i.payload #>> '{payload,wasPaid}' = 'true'))))
                ), effects as (
                    select case
                        when exists (select 1 from paid_orders other where other.order_id = p.order_id
                            and (other.seller_id, other.payment_id, other.gross_vnd, other.commission_bps)
                                is distinct from (p.seller_id, p.payment_id, p.gross_vnd, p.commission_bps))
                            then 'UNVERIFIED'
                        when s.order_id is not null and (s.seller_id, s.payment_id, s.gross_amount_vnd, s.commission_bps_snapshot)
                            is distinct from (p.seller_id, p.payment_id, p.gross_vnd, p.commission_bps) then 'UNVERIFIED'
                        when s.order_id is not null then 'SATISFIED'
                        when p.status in ('APPLIED', 'FAILED_REVIEW') then 'UNVERIFIED'
                        else 'WAITING'
                    end as state
                    from paid_orders p
                    left join finance.settlement s on s.order_id = p.order_id
                    where p.status in ('WAITING_DEPENDENCY', 'FAILED_REVIEW', 'APPLIED')
                        and (p.seller_id = :seller or s.seller_id = :seller)
                    union all
                    select case
                        when s.order_id is not null and p.order_id is not null
                            and (s.seller_id, s.payment_id, s.gross_amount_vnd, s.commission_bps_snapshot)
                                is distinct from (p.seller_id, p.payment_id, p.gross_vnd, p.commission_bps) then 'UNVERIFIED'
                        when e.event_type = 'refund.succeeded' and (s.order_id is not null or p.order_id is not null)
                            and (e.payment_id, e.amount_vnd)
                                is distinct from (coalesce(s.payment_id, p.payment_id), coalesce(s.gross_amount_vnd, p.gross_vnd))
                            then 'UNVERIFIED'
                        when e.event_type <> 'refund.succeeded'
                            and e.seller_id is distinct from coalesce(s.seller_id, p.seller_id, e.seller_id) then 'UNVERIFIED'
                        when e.event_type = 'order.cancelled' and (s.order_id is not null or p.order_id is not null)
                            and e.cancelled_amount_vnd is distinct from coalesce(s.gross_amount_vnd, p.gross_vnd)
                            then 'UNVERIFIED'
                        when e.event_type = 'refund.succeeded' and s.status = 'REFUNDED' and exists (
                            select 1 from finance.wallet_transaction t where t.settlement_id = s.settlement_id
                                and t.transaction_type = 'REFUND_DEBIT' and t.reference_id = e.refund_id) then 'SATISFIED'
                        when e.event_type = 'order.confirmed' and s.status = 'SETTLED' then 'SATISFIED'
                        when e.event_type = 'order.cancelled' and s.status in ('PENDING_REFUND', 'REFUNDED') then 'SATISFIED'
                        when e.status = 'FAILED_REVIEW' or s.status in ('SETTLED', 'REFUNDED')
                            or (e.event_type = 'order.confirmed' and s.status = 'PENDING_REFUND') then 'UNVERIFIED'
                        else 'WAITING'
                    end as state
                    from lifecycle_events e
                    left join finance.settlement s on s.order_id = e.order_id
                    left join paid_orders p on p.order_id = e.order_id
                    where s.seller_id = :seller or p.seller_id = :seller
                        or (s.order_id is null and p.order_id is null and e.seller_id = :seller
                            and e.event_type <> 'refund.succeeded')
                )
                select w.pending_balance_vnd, w.available_balance_vnd, w.row_version,
                    w.pending_balance_vnd > 0
                    or exists (select 1 from finance.settlement s where s.seller_id = :seller
                        and s.status in ('PENDING', 'PENDING_REFUND'))
                    or exists (select 1 from effects where state = 'WAITING'),
                    exists (select 1 from effects where state = 'UNVERIFIED')
                from finance.wallet w where w.owner_type = 'SELLER' and w.currency = 'VND' and w.owner_id = :seller
                """).setParameter("seller", sellerId).getResultList();
        if (list.isEmpty()) return SellerFinanceController.error(404, "NOT_FOUND", request);
        if (list.size() != 1) return SellerFinanceController.error(503, "SERVICE_UNAVAILABLE", request);
        var row = (Object[]) list.getFirst();
        if ((Boolean) row[4]) return SellerFinanceController.error(503, "SERVICE_UNAVAILABLE", request);
        return ResponseEntity.ok(Map.of("data", new Snapshot((Boolean) row[3], ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(), Instant.now(), ((Number) row[2]).longValue())));
    }

    @ExceptionHandler({PersistenceException.class, DataAccessException.class, TransactionException.class})
    ResponseEntity<?> unavailable(HttpServletRequest request) {
        return SellerFinanceController.error(503, "SERVICE_UNAVAILABLE", request);
    }

    record Snapshot(boolean hasPendingFinance, long pendingBalanceVnd, long availableBalanceVnd,
                    Instant evaluatedAt, long financeStateVersion) {}
}
