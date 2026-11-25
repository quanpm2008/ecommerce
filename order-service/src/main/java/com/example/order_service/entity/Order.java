package com.example.order_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_order")
public class Order {
	@Id
	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	@Column(name = "checkout_session_id", nullable = false)
	private UUID checkoutSessionId;

	@Column(name = "seller_id", nullable = false)
	private UUID sellerId;

	@Column(name = "seller_name_snapshot", nullable = false, length = 150)
	private String sellerNameSnapshot;

	@Column(name = "customer_id", nullable = false)
	private UUID customerId;

	@Column(name = "total_amount_vnd", nullable = false)
	private long totalAmountVnd;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "VND";

	@Column(name = "commission_bps_snapshot", nullable = false)
	private int commissionBpsSnapshot;

	@Column(name = "recipient_name", nullable = false, length = 150)
	private String recipientName;

	@Column(name = "recipient_phone", nullable = false, length = 20)
	private String recipientPhone;

	@Column(name = "province_code", nullable = false, length = 32)
	private String provinceCode;

	@Column(name = "province_name", nullable = false, length = 150)
	private String provinceName;

	@Column(name = "ward_code", nullable = false, length = 32)
	private String wardCode;

	@Column(name = "ward_name", nullable = false, length = 150)
	private String wardName;

	@Column(name = "address_detail", nullable = false, length = 300)
	private String addressDetail;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 24)
	private Status status;

	@Column(name = "cancel_reason_code", length = 50)
	private String cancelReasonCode;

	@Column(name = "cancel_reason_note", length = 500)
	private String cancelReasonNote;

	@Enumerated(EnumType.STRING)
	@Column(name = "cancelled_by_type", length = 16)
	private CancelledByType cancelledByType;

	@Column(name = "cancelled_by")
	private UUID cancelledBy;

	@Column(name = "paid_at")
	private Instant paidAt;

	@Column(name = "packing_at")
	private Instant packingAt;

	@Column(name = "delivered_at")
	private Instant deliveredAt;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "confirmed_by", length = 16)
	private ConfirmedBy confirmedBy;

	@Column(name = "seller_action_due_at")
	private Instant sellerActionDueAt;

	@Column(name = "customer_confirm_due_at")
	private Instant customerConfirmDueAt;

	@Column(name = "needs_manual_review", nullable = false)
	private boolean needsManualReview;

	@Column(name = "manual_note", columnDefinition = "text")
	private String manualNote;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "lifecycle_version", nullable = false)
	private long lifecycleVersion;

	protected Order() {
	}

	public Order(UUID orderId, UUID checkoutSessionId, UUID sellerId, String sellerNameSnapshot,
			UUID customerId, long totalAmountVnd, int commissionBpsSnapshot, String recipientName,
			String recipientPhone, String provinceCode, String provinceName, String wardCode,
			String wardName, String addressDetail, Status status, Instant createdAt,
			Instant updatedAt) {
		this.orderId = orderId;
		this.checkoutSessionId = checkoutSessionId;
		this.sellerId = sellerId;
		this.sellerNameSnapshot = sellerNameSnapshot;
		this.customerId = customerId;
		this.totalAmountVnd = totalAmountVnd;
		this.commissionBpsSnapshot = commissionBpsSnapshot;
		this.recipientName = recipientName;
		this.recipientPhone = recipientPhone;
		this.provinceCode = provinceCode;
		this.provinceName = provinceName;
		this.wardCode = wardCode;
		this.wardName = wardName;
		this.addressDetail = addressDetail;
		this.status = status;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public UUID getCheckoutSessionId() {
		return checkoutSessionId;
	}

	public UUID getSellerId() {
		return sellerId;
	}

	public String getSellerNameSnapshot() {
		return sellerNameSnapshot;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public long getTotalAmountVnd() {
		return totalAmountVnd;
	}

	public String getCurrency() {
		return currency;
	}

	public int getCommissionBpsSnapshot() {
		return commissionBpsSnapshot;
	}

	public String getRecipientName() {
		return recipientName;
	}

	public String getRecipientPhone() {
		return recipientPhone;
	}

	public String getProvinceCode() {
		return provinceCode;
	}

	public String getProvinceName() {
		return provinceName;
	}

	public String getWardCode() {
		return wardCode;
	}

	public String getWardName() {
		return wardName;
	}

	public String getAddressDetail() {
		return addressDetail;
	}

	public Status getStatus() {
		return status;
	}

	public String getCancelReasonCode() {
		return cancelReasonCode;
	}

	public String getCancelReasonNote() {
		return cancelReasonNote;
	}

	public CancelledByType getCancelledByType() {
		return cancelledByType;
	}

	public UUID getCancelledBy() {
		return cancelledBy;
	}

	public Instant getPaidAt() {
		return paidAt;
	}

	public Instant getPackingAt() {
		return packingAt;
	}

	public Instant getDeliveredAt() {
		return deliveredAt;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	public ConfirmedBy getConfirmedBy() {
		return confirmedBy;
	}

	public Instant getSellerActionDueAt() {
		return sellerActionDueAt;
	}

	public Instant getCustomerConfirmDueAt() {
		return customerConfirmDueAt;
	}

	public boolean getNeedsManualReview() {
		return needsManualReview;
	}

	public String getManualNote() {
		return manualNote;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public long getLifecycleVersion() {
		return lifecycleVersion;
	}

	public void cancel(CancelledByType by, UUID actorId, String reason, String note, Instant now) {
		moveTo(Status.CANCELLED, now);
		cancelledByType = by;
		cancelledBy = actorId;
		cancelReasonCode = reason;
		cancelReasonNote = note;
		cancelledAt = now;
	}

	public void startPacking(Instant now) {
		moveTo(Status.PACKING, now);
		packingAt = now;
	}

	public void markDelivered(Instant now, Instant due) {
		moveTo(Status.DELIVERED, now);
		deliveredAt = now;
		customerConfirmDueAt = due;
	}

	public void confirmReceipt(ConfirmedBy by, Instant now) {
		moveTo(Status.CONFIRMED, now);
		confirmedBy = by;
		confirmedAt = now;
	}

	private void moveTo(Status target, Instant now) {
		status = target;
		updatedAt = now;
		lifecycleVersion++;
	}

	public void closeAwaiting(Status closedStatus, Instant at) {
		if (status != Status.AWAITING_PAYMENT) {
			throw new IllegalStateException("Only an awaiting order can close with its checkout");
		}
		status = closedStatus;
		updatedAt = at;
	}

	public void markPaid(Instant completedAt, Instant dueAt, Instant updatedAt) {
		if (status != Status.AWAITING_PAYMENT) {
			throw new IllegalStateException("Only an awaiting order can be paid with its checkout");
		}
		status = Status.PAID;
		paidAt = completedAt;
		sellerActionDueAt = dueAt;
		this.updatedAt = updatedAt;
	}

	public enum Status { AWAITING_PAYMENT, PAYMENT_FAILED, EXPIRED, PAID, PACKING, DELIVERED, CONFIRMED, CANCELLED }
	public enum CancelledByType { CUSTOMER, SELLER, SYSTEM, ADMIN }
	public enum ConfirmedBy { CUSTOMER, SYSTEM }
}
