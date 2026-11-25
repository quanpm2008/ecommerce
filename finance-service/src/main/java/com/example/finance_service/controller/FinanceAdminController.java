package com.example.finance_service.controller;

import com.example.finance_service.client.FinanceAdminIdentityClient;
import com.example.finance_service.entity.Settlement;
import com.example.finance_service.entity.Wallet.OwnerType;
import com.example.finance_service.entity.WalletTransaction;
import com.example.finance_service.repository.WalletRepository;
import com.example.finance_service.security.FinanceAuthorization;
import com.example.finance_service.security.FinanceAuthorization.Rejected;
import com.example.finance_service.service.FinanceLedgerSnapshot;
import com.example.finance_service.service.FinanceReconciliationService;
import com.example.finance_service.service.SellerFinanceReadService;
import com.example.finance_service.service.SellerFinanceReadService.Dates;
import feign.FeignException;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance/admin")
class FinanceAdminController {
    private final FinanceAuthorization auth;
    private final FinanceLedgerSnapshot ledger;
    private final FinanceReconciliationService reconciliation;
    private final SellerFinanceReadService reads;
    private final WalletRepository wallets;
    private final FinanceAdminIdentityClient identity;

    FinanceAdminController(FinanceAuthorization auth, FinanceLedgerSnapshot ledger,
            FinanceReconciliationService reconciliation, SellerFinanceReadService reads, WalletRepository wallets,
            FinanceAdminIdentityClient identity) {
        this.auth = auth;
        this.ledger = ledger;
        this.reconciliation = reconciliation;
        this.reads = reads;
        this.wallets = wallets;
        this.identity = identity;
    }

    @GetMapping("/summary")
    Map<String, Object> summary(HttpServletRequest request) {
        authorize(request);
        var totals = ledger.totals();
        return Map.of("data", Map.of("sellerPendingVnd", totals.sellerPendingVnd(),
                "platformPendingVnd", totals.platformPendingVnd(), "commissionSettledVnd", totals.commissionSettledVnd(),
                "sellerAvailableVnd", totals.sellerAvailableVnd(), "totalWithdrawnVnd", totals.totalWithdrawnVnd(),
                "frozenWalletCount", totals.frozenWalletCount(), "frozenAvailableVnd", totals.frozenAvailableVnd(),
                "isSimulated", true));
    }

    @GetMapping("/reconciliation")
    Map<String, Object> reconciliation(HttpServletRequest request) {
        authorize(request);
        return Map.of("data", reconciliation.reconcile());
    }

    @GetMapping("/wallets")
    Map<String, Object> wallets(@RequestParam(required = false) OwnerType ownerType,
            @RequestParam(required = false) UUID sellerId, @RequestParam(required = false) Boolean frozen,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        String bearer = authorize(request);
        var rows = wallets.findAll((root, query, cb) -> {
            var conditions = new ArrayList<Predicate>();
            if (ownerType != null) conditions.add(cb.equal(root.get("ownerType"), ownerType));
            if (sellerId != null) conditions.add(cb.equal(root.get("ownerId"), sellerId));
            if (frozen != null) conditions.add(cb.equal(root.get("frozen"), frozen));
            return cb.and(conditions.toArray(Predicate[]::new));
        }, SellerFinanceReadService.page(page, size, "createdAt", "walletId"));
        return SellerFinanceReadService.paged(rows, wallet -> reads.walletView(wallet, false, false,
                wallet.getOwnerId() == null ? null : sellerName(wallet.getOwnerId(), bearer)));
    }

    @GetMapping("/wallets/{id}/transactions")
    Map<String, Object> transactions(@PathVariable UUID id, @RequestParam(required = false) WalletTransaction.TransactionType type,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        authorize(request);
        return reads.transactions(id, type, new Dates(from, to), page, size);
    }

    @GetMapping("/withdrawals")
    Map<String, Object> withdrawals(@RequestParam(required = false) UUID sellerId,
            @RequestParam(required = false) String withdrawalCode,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        authorize(request);
        return reads.withdrawals(sellerId, withdrawalCode, new Dates(from, to), page, size);
    }

    @GetMapping("/settlements")
    Map<String, Object> settlements(@RequestParam(required = false) UUID sellerId, @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) List<Settlement.Status> status, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        authorize(request);
        return reads.settlements(sellerId, orderId, status, new Dates(null, null), page, size, false);
    }

    private String authorize(HttpServletRequest request) {
        String bearer = SellerFinanceController.header(request, "Authorization");
        auth.admin(bearer);
        return bearer;
    }

    private String sellerName(UUID sellerId, String bearer) {
        try {
            var response = identity.seller(sellerId, bearer);
            var data = response == null ? null : response.data();
            if (data != null && data.user() != null && sellerId.equals(data.user().userId())
                    && data.sellerProfile() != null && data.sellerProfile().sellerName() != null) {
                return data.sellerProfile().sellerName();
            }
        } catch (FeignException unavailable) {
            throw new Rejected(503, "SERVICE_UNAVAILABLE");
        }
        throw new Rejected(503, "SERVICE_UNAVAILABLE");
    }

    @ExceptionHandler(Rejected.class)
    ResponseEntity<?> rejected(Rejected failure, HttpServletRequest request) {
        return SellerFinanceController.error(failure.status(), failure.getMessage(), request);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable(DataAccessException failure, HttpServletRequest request) {
        return SellerFinanceController.error(503, "SERVICE_UNAVAILABLE", request);
    }
}
