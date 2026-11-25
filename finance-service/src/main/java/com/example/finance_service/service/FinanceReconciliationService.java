package com.example.finance_service.service;

import com.example.finance_service.client.FinanceReconciliationSourceClients.OrderTotals;
import com.example.finance_service.client.FinanceReconciliationSourceClients.Orders;
import com.example.finance_service.client.FinanceReconciliationSourceClients.PaymentTotals;
import com.example.finance_service.client.FinanceReconciliationSourceClients.Payments;
import com.example.finance_service.service.FinanceLedgerSnapshot.InconsistentWallet;
import com.example.finance_service.service.FinanceLedgerSnapshot.Snapshot;
import feign.FeignException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

@Service
public class FinanceReconciliationService {
    private final Orders orders;
    private final Payments payments;
    private final FinanceLedgerSnapshot ledger;
    private final String orderToken;
    private final String paymentToken;

    FinanceReconciliationService(Orders orders, Payments payments, FinanceLedgerSnapshot ledger,
            @Value("${finance.order.credential:}") String orderToken,
            @Value("${finance.payment.credential:}") String paymentToken) {
        this.orders = orders;
        this.payments = payments;
        this.ledger = ledger;
        this.orderToken = orderToken;
        this.paymentToken = paymentToken;
    }

    public Report reconcile() {
        var order = order();
        var payment = payment();
        var local = local();
        var times = new LinkedHashMap<String, Instant>();
        var totals = new LinkedHashMap<String, Object>();
        var missing = new ArrayList<String>();
        if (order == null) missing.add("ORDER");
        else {
            times.put("ORDER", order.readAt());
            totals.put("ORDER", order);
        }
        if (payment == null) missing.add("PAYMENT");
        else {
            times.put("PAYMENT", payment.readAt());
            totals.put("PAYMENT", payment);
        }
        if (local == null) missing.add("FINANCE");
        else {
            times.put("FINANCE", local.totals().readAt());
            totals.put("FINANCE", local.totals());
        }
        var inconsistent = local == null ? List.<InconsistentWallet>of() : local.inconsistentWallets();
        Instant checked = Instant.now();
        if (!missing.isEmpty()) return new Report("INCOMPLETE", checked, times, missing, false, totals, null, inconsistent);
        var finance = local.totals();
        BigInteger difference = BigInteger.valueOf(payment.capturedVnd());
        for (long amount : new long[]{payment.refundedVnd(), payment.unsettledFullCheckoutRefundVnd(),
                finance.sellerPendingVnd(), finance.platformPendingVnd(), finance.commissionSettledVnd(),
                finance.sellerAvailableVnd(), finance.totalWithdrawnVnd()}) {
            difference = difference.subtract(BigInteger.valueOf(amount));
        }
        boolean balanced = difference.signum() == 0 && inconsistent.isEmpty();
        return new Report(balanced ? "COMPLETE" : "DIFFERENCE", checked, times, missing, balanced, totals, difference, inconsistent);
    }

    private OrderTotals order() {
        if (orderToken.isBlank()) return null;
        try {
            var envelope = orders.totals(orderToken);
            var data = envelope == null ? null : envelope.data();
            return data != null && data.readAt() != null && amounts(data.paidCheckoutCount(), data.paidCheckoutTotalVnd())
                    ? data : null;
        } catch (FeignException unavailable) {
            return null;
        }
    }

    private PaymentTotals payment() {
        if (paymentToken.isBlank()) return null;
        try {
            var envelope = payments.totals(paymentToken);
            var data = envelope == null ? null : envelope.data();
            return data != null && data.readAt() != null && amounts(data.capturedVnd(), data.refundedVnd(),
                    data.unsettledRefundVnd(), data.unsettledFullCheckoutRefundVnd())
                    && data.unsettledFullCheckoutRefundVnd() <= data.unsettledRefundVnd() ? data : null;
        } catch (FeignException unavailable) {
            return null;
        }
    }

    private Snapshot local() {
        try {
            return ledger.reconciliation();
        } catch (DataAccessException | TransactionException | ArithmeticException unavailable) {
            return null;
        }
    }

    private static boolean amounts(Long... amounts) {
        for (Long amount : amounts) if (amount == null || amount < 0) return false;
        return true;
    }

    public record Report(String status, Instant checkedAt, Map<String, Instant> sourceReadAt, List<String> missingSources,
            boolean balanced, Map<String, Object> totals, BigInteger differenceVnd, List<InconsistentWallet> inconsistentWallets) {}
}
