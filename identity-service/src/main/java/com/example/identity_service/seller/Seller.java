package com.example.identity_service.seller;

import com.example.identity_service.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Shop profile and application review (DB §3.1.3); {@code sellerId} is the owning {@code User.userId}. A profile
 * alone grants nothing: the SELLER role is granted only on approval.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "sellers")
public class Seller {

	@Id
	private UUID sellerId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "seller_id")
	private User user;

	private String sellerName;

	private String description;

	private String logoObjectKey;

	private String contactPhone;

	@Enumerated(EnumType.STRING)
	private SellerStatus status;

	private String banReason;

	@Enumerated(EnumType.STRING)
	private SellerApprovalStatus approvalStatus;

	private String rejectionReason;

	private Instant registeredAt;

	private Instant reviewedAt;

	private UUID reviewedByUserId;

	@CreationTimestamp
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

	/** A new PENDING application. {@code contactPhone} must already be normalized. */
	public Seller(User user, String sellerName, String contactPhone, String description) {
		this.user = user;
		this.sellerName = sellerName.strip();
		this.contactPhone = contactPhone;
		this.description = description;
		this.status = SellerStatus.ACTIVE;
		this.approvalStatus = SellerApprovalStatus.PENDING;
		this.registeredAt = Instant.now();
	}


	/** Only APPROVED and ACTIVE sellers may run their shop (DB §3.1.3). */
	public boolean canOperate() {
		return approvalStatus == SellerApprovalStatus.APPROVED && status == SellerStatus.ACTIVE;
	}

	/** A REJECTED application submitted again: same seller id, back to PENDING, review fields cleared. */
	public void resubmit(String sellerName, String contactPhone, String description) {
		if (approvalStatus != SellerApprovalStatus.REJECTED) {
			throw new IllegalStateException("Only a REJECTED application can be resubmitted");
		}
		this.sellerName = sellerName.strip();
		this.contactPhone = contactPhone;
		this.description = description;
		this.approvalStatus = SellerApprovalStatus.PENDING;
		this.rejectionReason = null;
		this.reviewedAt = null;
		this.reviewedByUserId = null;
		this.registeredAt = Instant.now();
	}

	public boolean isPending() {
		return approvalStatus == SellerApprovalStatus.PENDING;
	}

	/** PENDING → APPROVED; the SELLER role is granted by the caller in the same transaction. */
	public void approve(UUID reviewerId, Instant now) {
		requirePending();
		this.approvalStatus = SellerApprovalStatus.APPROVED;
		this.reviewedAt = now;
		this.reviewedByUserId = reviewerId;
	}

	/** PENDING → REJECTED; the partial unique indexes then free the shop name and contact phone. */
	public void reject(UUID reviewerId, String reason, Instant now) {
		requirePending();
		this.approvalStatus = SellerApprovalStatus.REJECTED;
		this.rejectionReason = Objects.requireNonNull(reason);
		this.reviewedAt = now;
		this.reviewedByUserId = reviewerId;
	}

	/** Blocks selling and withdrawals (G-14); approval and the SELLER role are kept. */
	public void ban(String reason) {
		this.status = SellerStatus.BANNED;
		this.banReason = Objects.requireNonNull(reason);
	}

	public void unban() {
		this.status = SellerStatus.ACTIVE;
		this.banReason = null;
	}

	private void requirePending() {
		if (!isPending()) {
			throw new IllegalStateException("Only a PENDING application can be reviewed");
		}
	}

	public void changeDescription(String description) {
		this.description = description;
	}

	/** {@code logoObjectKey} must already be verified; null removes the logo. */
	public void changeLogo(String logoObjectKey) {
		this.logoObjectKey = logoObjectKey;
	}

	/** {@code contactPhone} must already be normalized. */
	public void changeContactPhone(String contactPhone) {
		this.contactPhone = contactPhone;
	}

}
