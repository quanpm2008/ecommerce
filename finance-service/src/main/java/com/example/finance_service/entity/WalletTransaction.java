package com.example.finance_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "wallet_transaction")
public class WalletTransaction {
	@Id
	@Column(name = "wallet_transaction_id", nullable = false)
	private UUID walletTransactionId;

	@Column(name = "wallet_id", nullable = false)
	private UUID walletId;

	@Column(name = "settlement_id")
	private UUID settlementId;

	@Column(name = "withdrawal_id")
	private UUID withdrawalId;

	@Enumerated(EnumType.STRING)
	@Column(name = "reference_type", nullable = false, length = 12)
	private ReferenceType referenceType;

	@Column(name = "reference_id", nullable = false)
	private UUID referenceId;

	@Enumerated(EnumType.STRING)
	@Column(name = "transaction_type", nullable = false, length = 24)
	private TransactionType transactionType;

	@Column(name = "pending_delta_vnd", nullable = false)
	private long pendingDeltaVnd;

	@Column(name = "available_delta_vnd", nullable = false)
	private long availableDeltaVnd;

	@Column(name = "withdrawn_delta_vnd", nullable = false)
	private long withdrawnDeltaVnd;

	@Column(name = "business_event_key", nullable = false, length = 160)
	private String businessEventKey;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected WalletTransaction() {
	}

	public WalletTransaction(UUID walletTransactionId, UUID walletId, UUID settlementId, UUID withdrawalId,
			ReferenceType referenceType, UUID referenceId, TransactionType transactionType, long pendingDeltaVnd,
			long availableDeltaVnd, long withdrawnDeltaVnd, String businessEventKey, Instant createdAt) {
		this.walletTransactionId = walletTransactionId;
		this.walletId = walletId;
		this.settlementId = settlementId;
		this.withdrawalId = withdrawalId;
		this.referenceType = referenceType;
		this.referenceId = referenceId;
		this.transactionType = transactionType;
		this.pendingDeltaVnd = pendingDeltaVnd;
		this.availableDeltaVnd = availableDeltaVnd;
		this.withdrawnDeltaVnd = withdrawnDeltaVnd;
		this.businessEventKey = businessEventKey;
		this.createdAt = createdAt;
	}

	public UUID getWalletTransactionId() {
		return walletTransactionId;
	}

	public UUID getWalletId() {
		return walletId;
	}

	public UUID getSettlementId() {
		return settlementId;
	}

	public UUID getWithdrawalId() {
		return withdrawalId;
	}

	public ReferenceType getReferenceType() {
		return referenceType;
	}

	public UUID getReferenceId() {
		return referenceId;
	}

	public TransactionType getTransactionType() {
		return transactionType;
	}

	public long getPendingDeltaVnd() {
		return pendingDeltaVnd;
	}

	public long getAvailableDeltaVnd() {
		return availableDeltaVnd;
	}

	public long getWithdrawnDeltaVnd() {
		return withdrawnDeltaVnd;
	}

	public String getBusinessEventKey() {
		return businessEventKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public enum ReferenceType { PAYMENT, ORDER, REFUND, WITHDRAWAL }
	public enum TransactionType { PAYMENT_RECEIVED, SETTLEMENT_RELEASED, REFUND_DEBIT, WITHDRAWAL_COMPLETED }
}
