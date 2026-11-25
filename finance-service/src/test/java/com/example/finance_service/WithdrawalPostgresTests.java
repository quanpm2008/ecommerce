package com.example.finance_service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WithdrawalPostgresTests extends FinancePhase4Fixture {
    @Test
    void concurrentKeysAndReplayOnlyMoveAvailableOnce() throws Exception {
        var seller = seller(20_000, false);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = pool.submit(() -> withdraw(seller, "tab-a", 15_000));
            var b = pool.submit(() -> withdraw(seller, "tab-b", 15_000));
            var first = a.get();
            var second = b.get();
            assertThat(List.of(first.statusCode(), second.statusCode())).containsExactlyInAnyOrder(201, 409);
            String key = first.statusCode() == 201 ? "tab-a" : "tab-b";
            var created = json(first.statusCode() == 201 ? first : second).path("data");
            var replay = withdraw(seller, key, 15_000);
            assertThat(replay.statusCode()).isEqualTo(200);
            assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
            assertThat(json(replay).path("data")).isEqualTo(created);
            assertThat(withdraw(seller, key, 14_000).statusCode()).isEqualTo(409);
        }
        assertThat(db.queryForObject("select available_balance_vnd from finance.wallet where owner_id=?", Long.class, seller))
                .isEqualTo(5_000);
        assertThat(countWithdrawals(seller)).isOne();
        assertThat(db.queryForObject("""
                select count(*) from finance.wallet_transaction t join finance.withdrawal w using (withdrawal_id)
                where w.seller_id=? and t.transaction_type='WITHDRAWAL_COMPLETED'
                """, Integer.class, seller)).isOne();
        assertThat(db.queryForObject("""
                select count(*) from finance.withdrawal_outbox e join finance.withdrawal w using (withdrawal_id)
                where w.seller_id=?
                """, Integer.class, seller)).isOne();
    }

    @Test
    void sameKeyConcurrentAndSmallBannedWithdrawal() throws Exception {
        var banned = seller(9_500, true);
        assertThat(withdraw(banned, "partial", 5_000).statusCode()).isEqualTo(422);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = pool.submit(() -> withdraw(banned, "same", 9_500));
            var b = pool.submit(() -> withdraw(banned, "same", 9_500));
            var first = a.get();
            var second = b.get();
            assertThat(List.of(first.statusCode(), second.statusCode())).containsExactlyInAnyOrder(201, 200);
            assertThat(json(first).path("data")).isEqualTo(json(second).path("data"));
        }
        assertThat(countWithdrawals(banned)).isOne();
        assertThat(withdraw(seller(9_500, false), "active", 9_500).statusCode()).isEqualTo(422);
    }

    @Test
    void inconsistentFrozenAndFailedOutboxDoNotChangeWallet() throws Exception {
        var seller = seller(20_000, false);
        db.update("update finance.wallet set available_balance_vnd=19000 where owner_id=?", seller);
        assertThat(json(withdraw(seller, "invalid", 10_000)).path("error").path("code").asString()).isEqualTo("WALLET_INCONSISTENT");
        db.update("update finance.wallet set available_balance_vnd=20000, frozen=true where owner_id=?", seller);
        assertThat(json(withdraw(seller, "frozen", 10_000)).path("error").path("code").asString()).isEqualTo("WALLET_FROZEN");
        db.update("update finance.wallet set frozen=false where owner_id=?", seller);
        db.execute("alter table finance.withdrawal_outbox add constraint phase4_outbox_fail check(false) not valid");
        try {
            assertThat(withdraw(seller, "rollback", 10_000).statusCode()).isEqualTo(503);
            assertThat(countWithdrawals(seller)).isZero();
            assertThat(db.queryForObject("select available_balance_vnd from finance.wallet where owner_id=?", Long.class, seller))
                    .isEqualTo(20_000);
        } finally {
            db.execute("alter table finance.withdrawal_outbox drop constraint phase4_outbox_fail");
        }
    }
}
