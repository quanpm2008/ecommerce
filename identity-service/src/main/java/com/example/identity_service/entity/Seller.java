package com.example.identity_service.entity;

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
 * The shop profile of a SELLER account (DB §3.1.3); {@code sellerId} is the account's {@code User.userId}. The shop
 * name, contact phone and logo are the account's own {@code fullName}, {@code phoneNumber} and
 * {@code avatarObjectKey}. {@code approvalStatus}, {@code registeredAt} and the review fields mirror the latest
 * {@link SellerApplication}; every submission keeps its own row. A profile alone grants nothing: the SELLER role is
 * granted only on approval.
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

	private String description;

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

	/** The profile of a new seller account, with its first application PENDING. */
	public Seller(User user, String description, Instant submittedAt) {
		this.user = user;
		this.description = description;
		this.status = SellerStatus.ACTIVE;
		this.approvalStatus = SellerApprovalStatus.PENDING;
		this.registeredAt = submittedAt;
	}

	/** Only APPROVED and ACTIVE sellers may run their shop (DB §3.1.3). */
	public boolean canOperate() {
		return approvalStatus == SellerApprovalStatus.APPROVED && status == SellerStatus.ACTIVE;
	}

	/**
	 * A new application after REJECTED (UC-A02.ALT-04): the profile follows it back to PENDING. The rejected
	 * application keeps its own row and reason.
	 */
	public void reapply(String description, Instant submittedAt) {
		if (approvalStatus != SellerApprovalStatus.REJECTED) {
			throw new IllegalStateException("Only a REJECTED seller applies again");
		}
		this.description = description;
		this.approvalStatus = SellerApprovalStatus.PENDING;
		this.rejectionReason = null;
		this.reviewedAt = null;
		this.reviewedByUserId = null;
		this.registeredAt = submittedAt;
	}

	public boolean isPending() {
		return approvalStatus == SellerApprovalStatus.PENDING;
	}

	/** PENDING → APPROVED; the caller approves the application and grants SELLER in the same transaction. */
	public void approve(UUID reviewerId, Instant now) {
		requirePending();
		this.approvalStatus = SellerApprovalStatus.APPROVED;
		this.reviewedAt = now;
		this.reviewedByUserId = reviewerId;
	}

	/** PENDING → REJECTED; the shop name is then free for another seller. */
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

}
