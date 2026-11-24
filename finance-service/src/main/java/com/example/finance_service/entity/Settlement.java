package com.example.finance_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement")
public class Settlement {
	@Id
	@Column(name = "settlement_id", nullable = false)
	private UUID settlementId;

	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	@Column(name = "payment_id", nullable = false)
	private UUID paymentId;

	@Column(name = "seller_id", nullable = false)
	private UUID sellerId;

	@Column(name = "gross_amount_vnd", nullable = false)
	private long grossAmountVnd;

	@Column(name = "commission_bps_snapshot", nullable = false)
	private int commissionBpsSnapshot;

	@Column(name = "commission_amount_vnd", nullable = false)
	private long commissionAmountVnd;

	@Column(name = "seller_amount_vnd", nullable = false)
	private long sellerAmountVnd;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private Status status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "settled_at")
	private Instant settledAt;

	protected Settlement() {
	}

	public Settlement(UUID settlementId, UUID orderId, UUID paymentId, UUID sellerId,
			long grossAmountVnd, int commissionBpsSnapshot, long commissionAmountVnd, long sellerAmountVnd,
			Status status, Instant createdAt, Instant settledAt) {
		this.settlementId = settlementId;
		this.orderId = orderId;
		this.paymentId = paymentId;
		this.sellerId = sellerId;
		this.grossAmountVnd = grossAmountVnd;
		this.commissionBpsSnapshot = commissionBpsSnapshot;
		this.commissionAmountVnd = commissionAmountVnd;
		this.sellerAmountVnd = sellerAmountVnd;
		this.status = status;
		this.createdAt = createdAt;
		this.settledAt = settledAt;
	}

	public UUID getSettlementId() {
		return settlementId;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public UUID getPaymentId() {
		return paymentId;
	}

	public UUID getSellerId() {
		return sellerId;
	}

	public long getGrossAmountVnd() {
		return grossAmountVnd;
	}

	public int getCommissionBpsSnapshot() {
		return commissionBpsSnapshot;
	}

	public long getCommissionAmountVnd() {
		return commissionAmountVnd;
	}

	public long getSellerAmountVnd() {
		return sellerAmountVnd;
	}

	public Status getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getSettledAt() {
		return settledAt;
	}

	public void markPendingRefund() {
		status = Status.PENDING_REFUND;
	}

	public void markSettled(Instant now) {
		status = Status.SETTLED;
		settledAt = now;
	}

	public void markRefunded() {
		status = Status.REFUNDED;
	}

	public enum Status { PENDING, PENDING_REFUND, SETTLED, REFUNDED }
}
