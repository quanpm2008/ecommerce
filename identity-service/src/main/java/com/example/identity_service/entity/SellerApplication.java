package com.example.identity_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One submission of a seller application (DB §3.1.3): immutable except that a PENDING one is reviewed once (the
 * database refuses anything else). A REJECTED seller applies again with a new row, {@code submissionNo + 1}; the old
 * row keeps its decision and reason. At most one PENDING row per seller.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "seller_applications")
public class SellerApplication {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID applicationId;

	private UUID sellerId;

	private int submissionNo;

	@Enumerated(EnumType.STRING)
	private SellerApprovalStatus status;

	private String snapshotFullName;

	private String snapshotPhone;

	private String description;

	private Instant submittedAt;

	private Instant reviewedAt;

	private UUID reviewedByUserId;

	private String rejectionReason;

	/** A PENDING submission with the account's shop name and phone as they are now. */
	public SellerApplication(User seller, int submissionNo, String description, Instant submittedAt) {
		this.sellerId = seller.getUserId();
		this.submissionNo = submissionNo;
		this.status = SellerApprovalStatus.PENDING;
		this.snapshotFullName = seller.getFullName();
		this.snapshotPhone = Objects.requireNonNull(seller.getPhoneNumber());
		this.description = description;
		this.submittedAt = submittedAt;
	}

	public void approve(UUID reviewerId, Instant now) {
		review(SellerApprovalStatus.APPROVED, reviewerId, now);
	}

	public void reject(UUID reviewerId, String reason, Instant now) {
		review(SellerApprovalStatus.REJECTED, reviewerId, now);
		this.rejectionReason = Objects.requireNonNull(reason);
	}

	private void review(SellerApprovalStatus decision, UUID reviewerId, Instant now) {
		if (status != SellerApprovalStatus.PENDING) {
			throw new IllegalStateException("Application " + applicationId + " was already reviewed");
		}
		this.status = decision;
		this.reviewedByUserId = reviewerId;
		this.reviewedAt = now;
	}

}
