package com.example.finance_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "withdrawal")
public class Withdrawal {
	@Id
	@Column(name = "withdrawal_id", nullable = false)
	private UUID withdrawalId;

	@Column(name = "withdrawal_code", nullable = false, length = 32)
	private String withdrawalCode;

	@Column(name = "wallet_id", nullable = false)
	private UUID walletId;

	@Column(name = "seller_id", nullable = false)
	private UUID sellerId;

	@Column(name = "amount_vnd", nullable = false)
	private long amountVnd;

	@Column(name = "available_before_vnd", nullable = false)
	private long availableBeforeVnd;

	@Column(name = "available_after_vnd", nullable = false)
	private long availableAfterVnd;

	@Column(name = "destination", nullable = false, length = 100)
	private String destination;

	@Column(name = "idempotency_key", nullable = false, length = 128)
	private String idempotencyKey;

	@Column(name = "request_hash", nullable = false, length = 128)
	private String requestHash;

	@Column(name = "withdrawn_at", nullable = false)
	private Instant withdrawnAt;

	protected Withdrawal() {
	}

	public Withdrawal(UUID withdrawalId, String withdrawalCode, UUID walletId, UUID sellerId,
			long amountVnd, long availableBeforeVnd, long availableAfterVnd, String destination,
			String idempotencyKey, String requestHash, Instant withdrawnAt) {
		this.withdrawalId = withdrawalId;
		this.withdrawalCode = withdrawalCode;
		this.walletId = walletId;
		this.sellerId = sellerId;
		this.amountVnd = amountVnd;
		this.availableBeforeVnd = availableBeforeVnd;
		this.availableAfterVnd = availableAfterVnd;
		this.destination = destination;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.withdrawnAt = withdrawnAt;
	}

	public UUID getWithdrawalId() {
		return withdrawalId;
	}

	public String getWithdrawalCode() {
		return withdrawalCode;
	}

	public UUID getWalletId() {
		return walletId;
	}

	public UUID getSellerId() {
		return sellerId;
	}

	public long getAmountVnd() {
		return amountVnd;
	}

	public long getAvailableBeforeVnd() {
		return availableBeforeVnd;
	}

	public long getAvailableAfterVnd() {
		return availableAfterVnd;
	}

	public String getDestination() {
		return destination;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public Instant getWithdrawnAt() {
		return withdrawnAt;
	}
}
