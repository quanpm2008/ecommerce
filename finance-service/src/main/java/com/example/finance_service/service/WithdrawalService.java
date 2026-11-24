package com.example.finance_service.service;

import com.example.finance_service.entity.Wallet;
import com.example.finance_service.entity.Wallet.OwnerType;
import com.example.finance_service.entity.WalletTransaction;
import com.example.finance_service.entity.WalletTransaction.ReferenceType;
import com.example.finance_service.entity.WalletTransaction.TransactionType;
import com.example.finance_service.entity.Withdrawal;
import com.example.finance_service.repository.WalletRepository;
import com.example.finance_service.repository.WalletTransactionRepository;
import com.example.finance_service.repository.WithdrawalOutboxRepository;
import com.example.finance_service.repository.WithdrawalRepository;
import com.example.finance_service.security.FinanceAuthorization.Rejected;
import com.example.finance_service.security.FinanceAuthorization.SellerActor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalService {
    public static final long MINIMUM = 10_000;
    private final WalletRepository wallets;
    private final WalletTransactionRepository ledger;
    private final WithdrawalRepository withdrawals;
    private final WithdrawalOutboxRepository outbox;

    public WithdrawalService(WalletRepository wallets, WalletTransactionRepository ledger,
            WithdrawalRepository withdrawals, WithdrawalOutboxRepository outbox) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.withdrawals = withdrawals;
        this.outbox = outbox;
    }

    @Transactional
    public Result withdraw(SellerActor actor, String key, long amount) {
        if (!actor.canWithdraw()) throw new Rejected(403, "FORBIDDEN");
        if (amount <= 0) throw new Rejected(400, "VALIDATION_ERROR");
        var locked = wallets.findByOwnerTypeAndCurrencyAndOwnerIdInOrderByOwnerId(OwnerType.SELLER, "VND", List.of(actor.id()));
        if (locked.isEmpty()) throw new Rejected(409, "INSUFFICIENT_AVAILABLE_BALANCE");
        Wallet wallet = locked.getFirst();
        var previous = withdrawals.findBySellerIdAndIdempotencyKey(actor.id(), key);
        if (previous.isPresent()) {
            if (previous.get().getAmountVnd() != amount) throw new Rejected(409, "IDEMPOTENCY_KEY_REUSED");
            return new Result(previous.get(), true);
        }

        if (!consistent(wallet, ledger.totals(wallet.getWalletId()))) throw new Rejected(409, "WALLET_INCONSISTENT");
        if (wallet.isFrozen()) throw new Rejected(403, "WALLET_FROZEN");
        long available = wallet.getAvailableBalanceVnd();
        if (amount > available) throw new Rejected(409, "INSUFFICIENT_AVAILABLE_BALANCE");
        if (amount < MINIMUM && !(actor.banned() && amount == available)) {
            throw new Rejected(422, "WITHDRAWAL_BELOW_MINIMUM");
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID id = UUID.randomUUID();
        wallet.withdraw(amount, now);
        var withdrawal = withdrawals.saveAndFlush(new Withdrawal(id,
                "RT-" + id.toString().replace("-", "").substring(0, 24), wallet.getWalletId(), actor.id(),
                amount, available, wallet.getAvailableBalanceVnd(), "Ví ngoài giả lập", key, hash(amount), now));
        ledger.save(new WalletTransaction(UUID.randomUUID(), wallet.getWalletId(), null, id, ReferenceType.WITHDRAWAL,
                id, TransactionType.WITHDRAWAL_COMPLETED, 0, -amount, amount, "withdrawal.completed:" + id, now));
        outbox.enqueue(id, now);
        return new Result(withdrawal, false);
    }

    static boolean consistent(Wallet wallet, WalletTransactionRepository.Balances balances) {
        return wallet.getPendingBalanceVnd() == balances.getPending()
                && wallet.getAvailableBalanceVnd() == balances.getAvailable()
                && wallet.getTotalWithdrawnVnd() == balances.getWithdrawn();
    }

    private static String hash(long amount) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Long.toString(amount).getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Result(Withdrawal withdrawal, boolean replay) {}
}
