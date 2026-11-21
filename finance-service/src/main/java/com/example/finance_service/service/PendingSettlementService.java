package com.example.finance_service.service;

import com.example.finance_service.entity.Settlement;
import com.example.finance_service.entity.Wallet;
import com.example.finance_service.entity.WalletTransaction;
import com.example.finance_service.messaging.FinanceEvent.Approved;
import com.example.finance_service.messaging.FinanceEvent.Paid;
import com.example.finance_service.messaging.FinanceEvent.PaidOrder;
import com.example.finance_service.repository.SettlementInboxRepository;
import com.example.finance_service.repository.SettlementRepository;
import com.example.finance_service.repository.WalletRepository;
import com.example.finance_service.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PendingSettlementService {
	private final SettlementInboxRepository inbox;
	private final SettlementRepository settlements;
	private final WalletRepository wallets;
	private final WalletTransactionRepository ledger;

	public PendingSettlementService(SettlementInboxRepository inbox, SettlementRepository settlements,
			WalletRepository wallets, WalletTransactionRepository ledger) {
		this.inbox = inbox;
		this.settlements = settlements;
		this.wallets = wallets;
		this.ledger = ledger;
	}

	@Transactional
	public void checkoutPaid(Paid event, String raw) {
		inbox.receive(event.eventId(), "checkout.paid", raw);
		if (!inbox.isWaiting(event.eventId(), "checkout.paid", raw)) {
			return;
		}

		// All payments lock the platform before the seller wallets, then inspect the order business keys.
		var platform = wallets.findByOwnerTypeAndCurrency(Wallet.OwnerType.PLATFORM, "VND").orElse(null);
		Set<UUID> owners = event.orders().stream().map(PaidOrder::sellerId).collect(Collectors.toSet());
		var sellers = wallets.findByOwnerTypeAndCurrencyAndOwnerIdInOrderByOwnerId(Wallet.OwnerType.SELLER, "VND", owners)
				.stream().collect(Collectors.toMap(Wallet::getOwnerId, Function.identity()));
		if (platform == null || sellers.size() != owners.size()) {
			return;
		}

		var existing = settlements.findByOrderIdIn(event.orders().stream().map(PaidOrder::orderId).toList())
				.stream().collect(Collectors.toMap(Settlement::getOrderId, Function.identity()));
		for (var order : event.orders()) {
			var settlement = existing.get(order.orderId());
			if (settlement != null && !sameSnapshot(settlement, event.paymentId(), order)) {
				inbox.finish(event.eventId(), "FAILED_REVIEW");
				return;
			}
		}

		Instant now = Instant.now();
		for (var order : event.orders()) {
			if (existing.containsKey(order.orderId())) {
				continue;
			}
			long commission = commission(order.grossVnd(), order.commissionBps());
			long sellerAmount = order.grossVnd() - commission;
			var settlement = settlements.save(new Settlement(UUID.randomUUID(), order.orderId(), event.paymentId(),
					order.sellerId(), order.grossVnd(), order.commissionBps(), commission, sellerAmount,
					Settlement.Status.PENDING, now, null));
			credit(sellers.get(order.sellerId()), settlement, sellerAmount, now);
			credit(platform, settlement, commission, now);
		}
		inbox.finish(event.eventId(), "APPLIED");
	}

	@Transactional
	public void sellerApproved(Approved event, String raw) {
		inbox.receive(event.eventId(), "seller.approved", raw);
		if (!inbox.isWaiting(event.eventId(), "seller.approved", raw)) {
			return;
		}
		wallets.provisionApprovedSeller(UUID.randomUUID(), event.sellerId(), Instant.now());
		inbox.finish(event.eventId(), "APPLIED");
	}

	@Transactional
	public void retainInvalid(UUID id, String type, String raw) {
		inbox.retainInvalid(id, type, raw);
	}

	private void credit(Wallet wallet, Settlement settlement, long amountVnd, Instant now) {
		if (amountVnd == 0) {
			return;
		}
		wallet.creditPending(amountVnd, now);
		ledger.save(new WalletTransaction(UUID.randomUUID(), wallet.getWalletId(), settlement.getSettlementId(), null,
				WalletTransaction.ReferenceType.PAYMENT, settlement.getPaymentId(),
				WalletTransaction.TransactionType.PAYMENT_RECEIVED, amountVnd, 0, 0,
				"settlement:" + settlement.getOrderId() + ":" + wallet.getWalletId(), now));
	}

	private static boolean sameSnapshot(Settlement existing, UUID paymentId, PaidOrder order) {
		return paymentId.equals(existing.getPaymentId()) && order.sellerId().equals(existing.getSellerId())
				&& order.grossVnd() == existing.getGrossAmountVnd()
				&& order.commissionBps() == existing.getCommissionBpsSnapshot();
	}

	private static long commission(long gross, int bps) {
		// Split at the divisor so every intermediate fits in a signed bigint.
		return (gross / 10_000) * bps + ((gross % 10_000) * bps + 5_000) / 10_000;
	}
}
