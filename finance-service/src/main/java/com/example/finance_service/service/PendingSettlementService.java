package com.example.finance_service.service;

import com.example.finance_service.entity.Settlement;
import com.example.finance_service.entity.Wallet;
import com.example.finance_service.entity.WalletTransaction;
import com.example.finance_service.messaging.FinanceEvent.Approved;
import com.example.finance_service.messaging.FinanceEvent.Lifecycle;
import com.example.finance_service.messaging.FinanceEvent.Paid;
import com.example.finance_service.messaging.FinanceEvent.PaidOrder;
import com.example.finance_service.repository.SettlementInboxRepository;
import com.example.finance_service.repository.SettlementRepository;
import com.example.finance_service.repository.WalletRepository;
import com.example.finance_service.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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
	public void lifecycle(Lifecycle event, String raw) {
		inbox.receive(event.eventId(), event.type(), raw);
		if (!inbox.isWaiting(event.eventId(), event.type(), raw)) {
			return;
		}
		var settlement = settlements.findByOrderId(event.orderId()).orElse(null);
		if (settlement == null) {
			return;
		}
		boolean refund = "refund.succeeded".equals(event.type());
		boolean cancellation = "order.cancelled".equals(event.type());
		if (refund && (!settlement.getPaymentId().equals(event.paymentId())
				|| settlement.getGrossAmountVnd() != event.amountVnd())
				|| !refund && !settlement.getSellerId().equals(event.sellerId())
				|| cancellation && settlement.getGrossAmountVnd() != event.amountVnd()) {
			inbox.finish(event.eventId(), "FAILED_REVIEW");
			return;
		}
		var status = settlement.getStatus();
		if (cancellation) {
			if (status == Settlement.Status.PENDING) {
				settlement.markPendingRefund();
			}
			inbox.finish(event.eventId(), status == Settlement.Status.SETTLED ? "FAILED_REVIEW" : "APPLIED");
			return;
		}
		var target = refund ? Settlement.Status.REFUNDED : Settlement.Status.SETTLED;
		if (status == target) {
			boolean duplicate = !refund || ledger.existsBySettlementIdAndTransactionTypeAndReferenceId(
					settlement.getSettlementId(), WalletTransaction.TransactionType.REFUND_DEBIT, event.aggregateId());
			inbox.finish(event.eventId(), duplicate ? "APPLIED" : "FAILED_REVIEW");
			return;
		}
		if (refund && status == Settlement.Status.PENDING) {
			return;
		}
		var expected = refund ? Settlement.Status.PENDING_REFUND : Settlement.Status.PENDING;
		if (status != expected) {
			inbox.finish(event.eventId(), "FAILED_REVIEW");
			return;
		}
		var platform = wallets.findByOwnerTypeAndCurrency(Wallet.OwnerType.PLATFORM, "VND").orElse(null);
		var seller = wallets.findByOwnerTypeAndCurrencyAndOwnerIdInOrderByOwnerId(
				Wallet.OwnerType.SELLER, "VND", List.of(settlement.getSellerId())).stream().findFirst().orElse(null);
		if (platform == null || seller == null) {
			return;
		}
		Instant now = Instant.now();
		debit(platform, settlement, event, settlement.getCommissionAmountVnd(), refund, now);
		debit(seller, settlement, event, settlement.getSellerAmountVnd(), refund, now);
		if (refund) {
			settlement.markRefunded();
		} else {
			settlement.markSettled(now);
			inbox.enqueueFundsAvailable(settlement, now);
		}
		inbox.finish(event.eventId(), "APPLIED");
	}

	private void debit(Wallet wallet, Settlement settlement, Lifecycle event, long amount, boolean refund, Instant now) {
		if (amount == 0) {
			return;
		}
		wallet.debitPending(amount, !refund, now);
		var type = refund ? WalletTransaction.TransactionType.REFUND_DEBIT
				: WalletTransaction.TransactionType.SETTLEMENT_RELEASED;
		var reference = refund ? WalletTransaction.ReferenceType.REFUND : WalletTransaction.ReferenceType.ORDER;
		ledger.save(new WalletTransaction(UUID.randomUUID(), wallet.getWalletId(), settlement.getSettlementId(), null,
				reference, event.aggregateId(), type, -amount, refund ? 0 : amount, 0,
				type.name() + ":" + settlement.getOrderId() + ":" + wallet.getWalletId(), now));
	}

	@Transactional
	public void retainInvalid(UUID id, String type, String raw) {
		inbox.retainInvalid(id, type, raw);
	}

	@Transactional
	public void fundsAvailablePublished(UUID eventId) {
		inbox.markFundsAvailablePublished(eventId);
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
