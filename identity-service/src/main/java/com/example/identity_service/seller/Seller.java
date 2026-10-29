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

}
