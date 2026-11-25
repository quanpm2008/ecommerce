package com.example.finance_service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FinanceReadPostgresTests extends FinancePhase4Fixture {
    @Test
    void pendingOnlyAccessDoesNotShowSettledHistory() throws Exception {
        var owner = seller(9_500, true);
        PENDING_ONLY.add(owner);
        var settlements = get(owner, "/api/v1/finance/seller/settlements");
        assertThat(settlements.statusCode()).as(settlements.body()).isEqualTo(200);
        assertThat(json(settlements).path("data").size()).isZero();
        var ledger = get(owner, "/api/v1/finance/seller/wallet/transactions");
        assertThat(ledger.statusCode()).as(ledger.body()).isEqualTo(200);
        assertThat(json(ledger).path("data").size()).isZero();
        assertThat(withdraw(owner, "denied", 9_500).statusCode()).isEqualTo(403);
    }
    @Test
    void sellerCanReadOnlyOwnReceiptAndAvailableOnlyProjection() throws Exception {
        var owner = seller(9_500, true);
        var other = seller(20_000, false);
        var wallet = get(owner, "/api/v1/finance/seller/wallet");
        assertThat(wallet.statusCode()).isEqualTo(200);
        assertThat(json(wallet).path("data").path("allowBelowMinimumFullWithdrawal").asBoolean()).isTrue();
        assertThat(get(owner, "/api/v1/finance/seller/settlements").statusCode()).isEqualTo(403);
        assertThat(get(other, "/api/v1/finance/admin/summary").statusCode()).isEqualTo(403);
        var adminWallets = get(ADMIN, "/api/v1/finance/admin/wallets?sellerId=" + owner);
        assertThat(adminWallets.statusCode()).as(adminWallets.body()).isEqualTo(200);
        assertThat(json(adminWallets).path("data").get(0).path("sellerName").asString()).isEqualTo("Finance test seller");
        var withdrawal = withdraw(owner, "receipt", 9_500);
        assertThat(withdrawal.statusCode()).isEqualTo(201);
        var id = json(withdrawal).path("data").path("withdrawalId").asString();
        assertThat(get(other, "/api/v1/finance/seller/withdrawals/" + id).statusCode()).isEqualTo(404);
        assertThat(get(owner, "/api/v1/finance/seller/withdrawals/" + id).statusCode()).isEqualTo(200);
        var settlements = get(other, "/api/v1/finance/seller/settlements");
        assertThat(settlements.statusCode()).as(settlements.body()).isEqualTo(200);
        assertThat(json(settlements).path("summary").path("settledSellerAmountVnd").asLong()).isEqualTo(20_000);
    }
}
