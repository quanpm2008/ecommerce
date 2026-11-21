package com.example.identity_service.customer;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Address book entry (DB §3.1.4). Orders keep their own snapshot, so deleting an entry removes it (P-09, G-13).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "customer_addresses")
public class CustomerAddress {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID addressId;

	private UUID customerId;

	private String recipientName;

	private String recipientPhone;

	private String provinceCode;

	private String wardCode;

	private String addressDetail;

	private Instant lastUsedAt;

	@CreationTimestamp
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

	/** {@code recipientPhone} must already be normalized. */
	public CustomerAddress(UUID customerId, String recipientName, String recipientPhone, String provinceCode,
			String wardCode, String addressDetail) {
		this.customerId = customerId;
		this.recipientName = recipientName;
		this.recipientPhone = recipientPhone;
		this.provinceCode = provinceCode;
		this.wardCode = wardCode;
		this.addressDetail = addressDetail;
	}


	/** {@code recipientPhone} must already be normalized and the region already verified. */
	public void update(String recipientName, String recipientPhone, String provinceCode, String wardCode,
			String addressDetail) {
		this.recipientName = recipientName;
		this.recipientPhone = recipientPhone;
		this.provinceCode = provinceCode;
		this.wardCode = wardCode;
		this.addressDetail = addressDetail;
	}

	/** A committed checkout used this address; it becomes the default. */
	public void markUsed(Instant now) {
		this.lastUsedAt = now;
	}

}
