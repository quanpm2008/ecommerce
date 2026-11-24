package com.example.finance_service.service;

import com.example.finance_service.entity.Settlement;
import com.example.finance_service.entity.Wallet;
import com.example.finance_service.entity.Wallet.OwnerType;
import com.example.finance_service.entity.WalletTransaction;
import com.example.finance_service.entity.Withdrawal;
import com.example.finance_service.repository.SettlementRepository;
import com.example.finance_service.repository.WalletRepository;
import com.example.finance_service.repository.WalletTransactionRepository;
import com.example.finance_service.repository.WithdrawalRepository;
import com.example.finance_service.security.FinanceAuthorization.Rejected;
import com.example.finance_service.security.FinanceAuthorization.SellerActor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellerFinanceReadService {
    private final WalletRepository wallets;
    private final WalletTransactionRepository ledger;
    private final WithdrawalRepository withdrawals;
    private final SettlementRepository settlements;
    private final EntityManager entities;

    public SellerFinanceReadService(WalletRepository wallets, WalletTransactionRepository ledger,
            WithdrawalRepository withdrawals, SettlementRepository settlements, EntityManager entities) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.withdrawals = withdrawals;
        this.settlements = settlements;
        this.entities = entities;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> wallet(SellerActor actor) {
        return walletView(walletFor(actor.id()), actor.canWithdraw(), actor.banned(), null);
    }

    public Map<String, Object> walletView(Wallet wallet, boolean allowed, boolean banned, String sellerName) {
        long available = wallet.getAvailableBalanceVnd();
        boolean consistent = WithdrawalService.consistent(wallet, ledger.totals(wallet.getWalletId()));
        boolean small = allowed && consistent && !wallet.isFrozen() && banned && available > 0
                && available < WithdrawalService.MINIMUM;
        boolean canWithdraw = allowed && consistent && !wallet.isFrozen() && (available >= WithdrawalService.MINIMUM || small);
        var data = new LinkedHashMap<String, Object>();
        data.put("walletId", wallet.getWalletId());
        data.put("ownerType", wallet.getOwnerType());
        data.put("sellerId", wallet.getOwnerId());
        if (sellerName != null) data.put("sellerName", sellerName);
        data.put("pendingBalanceVnd", wallet.getPendingBalanceVnd());
        data.put("availableBalanceVnd", available);
        data.put("totalWithdrawnVnd", wallet.getTotalWithdrawnVnd());
        data.put("minWithdrawalVnd", WithdrawalService.MINIMUM);
        data.put("allowBelowMinimumFullWithdrawal", small);
        data.put("isFrozen", wallet.isFrozen());
        data.put("canWithdraw", canWithdraw);
        String reason = null;
        if (!canWithdraw) {
            if (!consistent) reason = "WALLET_INCONSISTENT";
            else if (wallet.isFrozen()) reason = "WALLET_FROZEN";
            else if (available < WithdrawalService.MINIMUM) {
                reason = wallet.getPendingBalanceVnd() > 0 ? "BELOW_MINIMUM_PENDING_EXISTS" : "BELOW_MINIMUM";
            }
        }
        data.put("withdrawDisabledReason", reason);
        data.put("isSimulated", true);
        return data;
    }

    public Map<String, Object> transactions(SellerActor actor, WalletTransaction.TransactionType type, Dates dates,
            int page, int size) {
        if (actor.withdrawalOnly()) throw new Rejected(403, "FORBIDDEN");
        return transactions(walletFor(actor.id()).getWalletId(), type, dates, page, size, actor.readOnly());
    }

    public Map<String, Object> transactions(UUID walletId, WalletTransaction.TransactionType type, Dates dates,
            int page, int size) {
        return transactions(walletId, type, dates, page, size, false);
    }

    private Map<String, Object> transactions(UUID walletId, WalletTransaction.TransactionType type, Dates dates,
            int page, int size, boolean pendingOnly) {
        if (!wallets.existsById(walletId)) throw missing();
        var result = ledger.findAll((root, query, cb) -> {
            var where = dates.filter(cb, root.get("createdAt"));
            where.add(cb.equal(root.get("walletId"), walletId));
            if (type != null) where.add(cb.equal(root.get("transactionType"), type));
            if (pendingOnly) {
                var pending = query.subquery(UUID.class);
                var source = pending.from(Settlement.class);
                pending.select(source.get("settlementId")).where(source.get("status").in(
                        Settlement.Status.PENDING, Settlement.Status.PENDING_REFUND));
                where.add(root.get("settlementId").in(pending));
            }
            return cb.and(where.toArray(Predicate[]::new));
        }, page(page, size, "createdAt", "walletTransactionId"));
        var ids = result.stream().map(WalletTransaction::getSettlementId).filter(java.util.Objects::nonNull).distinct().toList();
        var orders = new java.util.HashMap<UUID, UUID>();
        settlements.findAllById(ids).forEach(settlement -> orders.put(settlement.getSettlementId(), settlement.getOrderId()));
        return paged(result, value -> new TransactionView(value.getWalletTransactionId(), value.getTransactionType(),
                value.getReferenceType(), value.getReferenceId(), value.getSettlementId(), value.getWithdrawalId(),
                orders.get(value.getSettlementId()), value.getPendingDeltaVnd(), value.getAvailableDeltaVnd(),
                value.getWithdrawnDeltaVnd(), value.getCreatedAt()));
    }

    public Map<String, Object> withdrawals(UUID sellerId, Dates dates, int page, int size) {
        return withdrawals(sellerId, null, dates, page, size, false);
    }

    public Map<String, Object> withdrawals(UUID sellerId, String code, Dates dates, int page, int size) {
        return withdrawals(sellerId, code, dates, page, size, true);
    }

    private Map<String, Object> withdrawals(UUID sellerId, String code, Dates dates, int page, int size, boolean admin) {
        var result = withdrawals.findAll((root, query, cb) -> {
            var where = dates.filter(cb, root.get("withdrawnAt"));
            if (sellerId != null) where.add(cb.equal(root.get("sellerId"), sellerId));
            if (code != null) where.add(cb.equal(root.get("withdrawalCode"), code));
            return cb.and(where.toArray(Predicate[]::new));
        }, page(page, size, "withdrawnAt", "withdrawalId"));
        return paged(result, value -> {
            var data = receipt(value);
            if (admin) data.put("sellerId", value.getSellerId());
            return data;
        });
    }

    public Map<String, Object> receipt(UUID sellerId, UUID id) {
        return receipt(withdrawals.findByWithdrawalIdAndSellerId(id, sellerId).orElseThrow(SellerFinanceReadService::missing));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> settlements(UUID sellerId, UUID orderId, List<Settlement.Status> status,
            Dates dates, int page, int size, boolean includeSummary) {
        return settlementPage(sellerId, orderId, status, dates, page, size, includeSummary, false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> settlements(SellerActor actor, UUID orderId, List<Settlement.Status> status,
            Dates dates, int page, int size) {
        return settlementPage(actor.id(), orderId, status, dates, page, size, true, actor.readOnly());
    }

    private Map<String, Object> settlementPage(UUID sellerId, UUID orderId, List<Settlement.Status> status,
            Dates dates, int page, int size, boolean includeSummary, boolean pendingOnly) {
        Specification<Settlement> filter = (root, query, cb) -> {
            var date = status != null && status.equals(List.of(Settlement.Status.SETTLED)) ? "settledAt" : "createdAt";
            var where = dates.filter(cb, root.get(date));
            if (sellerId != null) where.add(cb.equal(root.get("sellerId"), sellerId));
            if (orderId != null) where.add(cb.equal(root.get("orderId"), orderId));
            if (status != null && !status.isEmpty()) where.add(root.get("status").in(status));
            if (pendingOnly) where.add(root.get("status").in(Settlement.Status.PENDING, Settlement.Status.PENDING_REFUND));
            return cb.and(where.toArray(Predicate[]::new));
        };
        var result = settlements.findAll(filter, page(page, size, "createdAt", "settlementId"));
        var response = paged(result, settlement -> settlementView(settlement, !includeSummary));
        if (includeSummary) response.put("summary", settlementSummary(filter));
        return response;
    }

    private Map<String, Object> settlementSummary(Specification<Settlement> filter) {
        var cb = entities.getCriteriaBuilder();
        var query = cb.createTupleQuery();
        var root = query.from(Settlement.class);
        var status = root.<Settlement.Status>get("status");
        var settled = cb.equal(status, Settlement.Status.SETTLED);
        var pending = status.in(Settlement.Status.PENDING, Settlement.Status.PENDING_REFUND);
        var refunded = cb.equal(status, Settlement.Status.REFUNDED);
        query.multiselect(
                cb.coalesce(cb.sum(cb.<Long>selectCase().when(settled, root.<Long>get("commissionAmountVnd")).otherwise(0L)), 0L),
                cb.coalesce(cb.sum(cb.<Long>selectCase().when(settled, root.<Long>get("sellerAmountVnd")).otherwise(0L)), 0L),
                cb.coalesce(cb.sum(cb.<Long>selectCase().when(pending, root.<Long>get("sellerAmountVnd")).otherwise(0L)), 0L),
                cb.coalesce(cb.sum(cb.<Long>selectCase().when(refunded, root.<Long>get("grossAmountVnd")).otherwise(0L)), 0L));
        query.where(filter.toPredicate(root, query, cb));
        var totals = entities.createQuery(query).getSingleResult();
        return Map.of("settledCommissionVnd", totals.get(0), "settledSellerAmountVnd", totals.get(1),
                "pendingSellerAmountVnd", totals.get(2), "refundedGrossVnd", totals.get(3));
    }

    private static Map<String, Object> settlementView(Settlement settlement, boolean admin) {
        var data = new LinkedHashMap<String, Object>();
        data.put("settlementId", settlement.getSettlementId());
        data.put("orderId", settlement.getOrderId());
        data.put("paymentId", settlement.getPaymentId());
        if (admin) data.put("sellerId", settlement.getSellerId());
        data.put("grossAmountVnd", settlement.getGrossAmountVnd());
        data.put("commissionBpsSnapshot", settlement.getCommissionBpsSnapshot());
        data.put("commissionAmountVnd", settlement.getCommissionAmountVnd());
        data.put("sellerAmountVnd", settlement.getSellerAmountVnd());
        data.put("status", settlement.getStatus());
        data.put("createdAt", settlement.getCreatedAt());
        data.put("settledAt", settlement.getSettledAt());
        data.put("isSimulated", true);
        return data;
    }

    public static Map<String, Object> receipt(Withdrawal withdrawal) {
        var data = new LinkedHashMap<String, Object>();
        data.put("withdrawalId", withdrawal.getWithdrawalId());
        data.put("withdrawalCode", withdrawal.getWithdrawalCode());
        data.put("amountVnd", withdrawal.getAmountVnd());
        data.put("availableBeforeVnd", withdrawal.getAvailableBeforeVnd());
        data.put("availableAfterVnd", withdrawal.getAvailableAfterVnd());
        data.put("destination", withdrawal.getDestination());
        data.put("withdrawnAt", withdrawal.getWithdrawnAt());
        data.put("isSimulated", true);
        return data;
    }

    private Wallet walletFor(UUID sellerId) {
        return wallets.findByOwnerTypeAndOwnerIdAndCurrency(OwnerType.SELLER, sellerId, "VND")
                .orElseThrow(SellerFinanceReadService::missing);
    }

    public static PageRequest page(int page, int size, String... sort) {
        if (page < 1 || size < 1 || size > 100) throw new Rejected(400, "VALIDATION_ERROR");
        return PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, sort));
    }

    public static <T> Map<String, Object> paged(Page<T> result, Function<T, ?> projection) {
        var response = new LinkedHashMap<String, Object>();
        response.put("data", result.stream().map(projection).toList());
        response.put("meta", Map.of("page", result.getNumber() + 1, "size", result.getSize(),
                "totalItems", result.getTotalElements(), "totalPages", result.getTotalPages()));
        return response;
    }

    private static Rejected missing() {
        return new Rejected(404, "NOT_FOUND");
    }

    public record Dates(LocalDate from, LocalDate to) {
        private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
        public Dates {
            if (from != null && to != null && from.isAfter(to)) throw new Rejected(400, "VALIDATION_ERROR");
        }
        ArrayList<Predicate> filter(CriteriaBuilder builder, Path<Instant> field) {
            var filters = new ArrayList<Predicate>();
            if (from != null) filters.add(builder.greaterThanOrEqualTo(field, from.atStartOfDay(ZONE).toInstant()));
            if (to != null) filters.add(builder.lessThan(field, to.plusDays(1).atStartOfDay(ZONE).toInstant()));
            return filters;
        }
    }

    private record TransactionView(UUID walletTransactionId, WalletTransaction.TransactionType transactionType,
            WalletTransaction.ReferenceType referenceType, UUID referenceId, UUID settlementId, UUID withdrawalId,
            UUID orderId, long pendingDeltaVnd, long availableDeltaVnd, long withdrawnDeltaVnd, Instant createdAt) {}
}
