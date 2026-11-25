package com.example.finance_service;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FinanceReconciliationPostgresTests extends FinancePhase4Fixture {
    @Test
    void reportsObservedDifferenceAndMissingSourcesWithoutChangingLedger() throws Exception {
        var seller = seller(20_000, false);
        var summary = json(get(ADMIN, "/api/v1/finance/admin/summary")).path("data");
        long accounted = summary.path("sellerPendingVnd").asLong() + summary.path("platformPendingVnd").asLong()
                + summary.path("commissionSettledVnd").asLong() + summary.path("sellerAvailableVnd").asLong()
                + summary.path("totalWithdrawnVnd").asLong();
        try {
            PAYMENT.set(Map.of("capturedVnd", accounted + 30_000, "refundedVnd", 20_000, "unsettledRefundVnd", 35_000,
                    "unsettledFullCheckoutRefundVnd", 10_000, "readAt", READ_TIME));
            var balanced = report();
            assertThat(balanced.path("status").asString()).isEqualTo("COMPLETE");
            assertThat(balanced.path("differenceVnd").asLong()).isZero();
            db.update("update finance.wallet set available_balance_vnd=19999 where owner_id=?", seller);
            var difference = report();
            assertThat(difference.path("status").asString()).isEqualTo("DIFFERENCE");
            assertThat(difference.path("differenceVnd").asLong()).isOne();
            PAYMENT.set(Map.of("capturedVnd", accounted + 29_999, "refundedVnd", 20_000, "unsettledRefundVnd", 35_000,
                    "unsettledFullCheckoutRefundVnd", 10_000, "readAt", READ_TIME));
            var withWalletDifference = report();
            assertThat(withWalletDifference.path("status").asString()).isEqualTo("DIFFERENCE");
            assertThat(withWalletDifference.path("differenceVnd").asLong()).isZero();
            assertThat(withWalletDifference.path("inconsistentWallets").toString()).contains(seller.toString());
            PAYMENT.set(null);
            var incomplete = report();
            assertThat(incomplete.path("status").asString()).isEqualTo("INCOMPLETE");
            assertThat(incomplete.path("missingSources").get(0).asString()).isEqualTo("PAYMENT");
            assertThat(incomplete.path("differenceVnd").isNull()).isTrue();
            assertThat(incomplete.path("totals").has("PAYMENT")).isFalse();
            assertThat(incomplete.path("sourceReadAt").has("ORDER")).isTrue();
            assertThat(db.queryForObject("select available_balance_vnd from finance.wallet where owner_id=?", Long.class, seller))
                    .isEqualTo(19_999);
        } finally {
            db.update("update finance.wallet set available_balance_vnd=20000 where owner_id=?", seller);
            PAYMENT.set(null);
        }
    }

    private tools.jackson.databind.JsonNode report() throws Exception {
        var response = get(ADMIN, "/api/v1/finance/admin/reconciliation");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json(response).path("data");
    }
}
