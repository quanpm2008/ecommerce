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
@Table(name = "checkout_session")
public class CheckoutSession {
	@Id
	@Column(name = "checkout_session_id", nullable = false)
	private UUID checkoutSessionId;

	@Column(name = "checkout_intent_id", nullable = false)
	private UUID checkoutIntentId;

	@Column(name = "customer_id", nullable = false)
	private UUID customerId;

	@Enumerated(EnumType.STRING)
	@Column(name = "checkout_source", nullable = false, length = 16)
	private Source checkoutSource;

	@Column(name = "source_cart_id")
	private UUID sourceCartId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "cart_snapshot", columnDefinition = "jsonb")
	private String cartSnapshot;

	@Column(name = "idempotency_key", nullable = false, length = 128)
	private String idempotencyKey;

	@Column(name = "request_hash", nullable = false, length = 128)
	private String requestHash;

	@Column(name = "total_amount_vnd", nullable = false)
	private long totalAmountVnd;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "VND";

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 24)
	private Status status;

	@Column(name = "payment_id")
	private UUID paymentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "close_reason", length = 24)
	private CloseReason closeReason;

	@Column(name = "address_id", nullable = false)
	private UUID addressId;

	@Column(name = "paid_at")
	private Instant paidAt;

	@Column(name = "needs_manual_review", nullable = false)
	private boolean needsManualReview;

	@Column(name = "manual_note", columnDefinition = "text")
	private String manualNote;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected CheckoutSession() {
	}

	public CheckoutSession(UUID checkoutSessionId, UUID checkoutIntentId, UUID customerId, Source checkoutSource,
			UUID sourceCartId, String cartSnapshot, String idempotencyKey, String requestHash, long totalAmountVnd, Status status,
			UUID addressId, Instant createdAt, Instant expiresAt) {
		this.checkoutSessionId = checkoutSessionId;
		this.checkoutIntentId = checkoutIntentId;
		this.customerId = customerId;
		this.checkoutSource = checkoutSource;
		this.sourceCartId = sourceCartId;
		this.cartSnapshot = cartSnapshot;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.totalAmountVnd = totalAmountVnd;
		this.status = status;
		this.addressId = addressId;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public UUID getCheckoutSessionId() {
		return checkoutSessionId;
	}

	public UUID getCheckoutIntentId() {
		return checkoutIntentId;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public Source getCheckoutSource() {
		return checkoutSource;
	}

	public UUID getSourceCartId() {
		return sourceCartId;
	}

	public String getCartSnapshot() {
		return cartSnapshot;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public long getTotalAmountVnd() {
		return totalAmountVnd;
	}

	public String getCurrency() {
		return currency;
	}

	public Status getStatus() {
		return status;
	}

	public UUID getPaymentId() {
		return paymentId;
	}

	public CloseReason getCloseReason() {
		return closeReason;
	}

	public UUID getAddressId() {
		return addressId;
	}

	public Instant getPaidAt() {
		return paidAt;
	}

	public boolean getNeedsManualReview() {
		return needsManualReview;
	}

	public String getManualNote() {
		return manualNote;
	}

	public void markCollectionHandled(String note) {
		this.needsManualReview = false;
		this.manualNote = note;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void bindPayment(UUID paymentId) {
		this.paymentId = paymentId;
	}

	public void close(CloseReason reason) {
		status = Status.CLOSED;
		closeReason = reason;
	}

	public void markPaid(Instant completedAt) {
		status = Status.PAID;
		paidAt = completedAt;
	}

	public enum Source { BUY_NOW, CART }
	public enum Status { AWAITING_PAYMENT, PAID, CLOSED }
	public enum CloseReason { PAYMENT_FAILED, EXPIRED }
}
