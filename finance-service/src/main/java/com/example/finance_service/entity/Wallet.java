package com.example.finance_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet")
public class Wallet {
	@Id
	@Column(name = "wallet_id", nullable = false)
	private UUID walletId;

	@Enumerated(EnumType.STRING)
	@Column(name = "owner_type", nullable = false, length = 12)
	private OwnerType ownerType;

	@Column(name = "owner_id")
	private UUID ownerId;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "VND";

	@Column(name = "pending_balance_vnd", nullable = false)
	private long pendingBalanceVnd;

	@Column(name = "available_balance_vnd", nullable = false)
	private long availableBalanceVnd;

	@Column(name = "total_withdrawn_vnd", nullable = false)
	private long totalWithdrawnVnd;

	@Column(name = "frozen", nullable = false)
	private boolean frozen;

	@Version
	@Column(name = "row_version", nullable = false)
	private long rowVersion;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Wallet() {
	}

	public Wallet(UUID walletId, OwnerType ownerType, UUID ownerId, Instant createdAt,
			Instant updatedAt) {
		this.walletId = walletId;
		this.ownerType = ownerType;
		this.ownerId = ownerId;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public UUID getWalletId() {
		return walletId;
	}

	public OwnerType getOwnerType() {
		return ownerType;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public String getCurrency() {
		return currency;
	}

	public long getPendingBalanceVnd() {
		return pendingBalanceVnd;
	}

	public long getAvailableBalanceVnd() {
		return availableBalanceVnd;
	}

	public long getTotalWithdrawnVnd() {
		return totalWithdrawnVnd;
	}

	public boolean isFrozen() {
		return frozen;
	}

	public void withdraw(long amount, Instant at) {
		if (amount <= 0 || amount > availableBalanceVnd) {
			throw new IllegalArgumentException("Invalid withdrawal amount");
		}
		availableBalanceVnd -= amount;
		totalWithdrawnVnd = Math.addExact(totalWithdrawnVnd, amount);
		updatedAt = at;
	}

	public long getRowVersion() {
		return rowVersion;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void creditPending(long amountVnd, Instant now) {
		pendingBalanceVnd = Math.addExact(pendingBalanceVnd, amountVnd);
		updatedAt = now;
	}

	public void debitPending(long amountVnd, boolean release, Instant now) {
		if (amountVnd <= 0 || pendingBalanceVnd < amountVnd) {
			throw new IllegalStateException("Invalid pending wallet debit");
		}
		pendingBalanceVnd -= amountVnd;
		if (release) {
			availableBalanceVnd = Math.addExact(availableBalanceVnd, amountVnd);
		}
		updatedAt = now;
	}

	public enum OwnerType { SELLER, PLATFORM }
}
