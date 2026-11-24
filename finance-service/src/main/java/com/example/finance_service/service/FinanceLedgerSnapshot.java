package com.example.finance_service.service;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceLedgerSnapshot {
    private final EntityManager entities;

    FinanceLedgerSnapshot(EntityManager entities) { this.entities = entities; }

    @Transactional(readOnly = true)
    public Totals totals() {
        return readTotals();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot reconciliation() {
        Totals totals = readTotals();
        var rows = entities.createNativeQuery("""
                with ledger as (
                    select wallet_id, sum(pending_delta_vnd) pending, sum(available_delta_vnd) available,
                           sum(withdrawn_delta_vnd) withdrawn from finance.wallet_transaction group by wallet_id
                ), settlements as (
                    select seller_id,
                       sum(seller_amount_vnd) filter (where status in ('PENDING','PENDING_REFUND')) pending,
                       sum(seller_amount_vnd) filter (where status='SETTLED') settled
                    from finance.settlement group by seller_id
                ), withdrawals as (
                    select seller_id, sum(amount_vnd) withdrawn from finance.withdrawal group by seller_id
                ), differences as (
                    select w.wallet_id, w.owner_id,
                        w.pending_balance_vnd-coalesce(l.pending,0) d1,
                        w.available_balance_vnd-coalesce(l.available,0) d2,
                        w.total_withdrawn_vnd-coalesce(l.withdrawn,0) d3,
                        case when w.owner_type='SELLER' then w.pending_balance_vnd-coalesce(s.pending,0) else 0 end d4,
                        case when w.owner_type='SELLER' then w.available_balance_vnd::numeric+w.total_withdrawn_vnd-coalesce(s.settled,0) else 0 end d5,
                        case when w.owner_type='SELLER' then w.total_withdrawn_vnd-coalesce(t.withdrawn,0) else 0 end d6
                    from finance.wallet w left join ledger l using (wallet_id)
                    left join settlements s on s.seller_id=w.owner_id left join withdrawals t on t.seller_id=w.owner_id
                )
                select wallet_id, owner_id, coalesce(nullif(d1,0),nullif(d2,0),nullif(d3,0),nullif(d4,0),nullif(d5,0),nullif(d6,0))
                from differences where d1<>0 or d2<>0 or d3<>0 or d4<>0 or d5<>0 or d6<>0 order by wallet_id
                """).getResultList();
        var inconsistent = rows.stream().map(Object[].class::cast).map(raw -> {
            var row = (Object[]) raw;
            return new InconsistentWallet((UUID) row[0], (UUID) row[1], amount(row[2]));
        }).toList();
        return new Snapshot(totals, inconsistent);
    }

    private Totals readTotals() {
        var row = (Object[]) entities.createNativeQuery("""
                select coalesce(sum(pending_balance_vnd) filter (where owner_type='SELLER'),0),
                       coalesce(sum(pending_balance_vnd) filter (where owner_type='PLATFORM'),0),
                       coalesce(sum(available_balance_vnd) filter (where owner_type='PLATFORM'),0),
                       coalesce(sum(available_balance_vnd) filter (where owner_type='SELLER'),0),
                       coalesce(sum(total_withdrawn_vnd) filter (where owner_type='SELLER'),0),
                       count(*) filter (where frozen),
                       coalesce(sum(available_balance_vnd) filter (where frozen),0),
                       statement_timestamp()
                from finance.wallet
                """).getSingleResult();
        Instant time = row[7] instanceof OffsetDateTime timestamp ? timestamp.toInstant() : (Instant) row[7];
        return new Totals(amount(row[0]), amount(row[1]), amount(row[2]), amount(row[3]), amount(row[4]),
                amount(row[5]), amount(row[6]), time);
    }

    private static long amount(Object value) { return new java.math.BigDecimal(value.toString()).longValueExact(); }

    public record Totals(long sellerPendingVnd, long platformPendingVnd, long commissionSettledVnd,
            long sellerAvailableVnd, long totalWithdrawnVnd, long frozenWalletCount, long frozenAvailableVnd, Instant readAt) {}
    public record InconsistentWallet(UUID walletId, UUID sellerId, long differenceVnd) {}
    public record Snapshot(Totals totals, List<InconsistentWallet> inconsistentWallets) {}
}
