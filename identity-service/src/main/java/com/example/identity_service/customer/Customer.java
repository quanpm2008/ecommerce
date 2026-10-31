package com.example.identity_service.customer;

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
 * Buyer profile (DB §3.1.2); {@code customerId} is the owning {@code User.userId}.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "customers")
public class Customer {

	@Id
	private UUID customerId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id")
	private User user;

	@Enumerated(EnumType.STRING)
	private CustomerStatus status;

	private String banReason;

	@Enumerated(EnumType.STRING)
	private CustomerBanMode banMode;

	private Boolean hasPaidOrderBeforeBan;

	@CreationTimestamp
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

	/** The CUSTOMER role must be granted in the same transaction. */
	public Customer(User user) {
		this.user = user;
		this.status = CustomerStatus.ACTIVE;
	}

	public boolean isBanned() {
		return status == CustomerStatus.BANNED;
	}

	/**
	 * G-14: the mode is fixed from Order's answer at ban time ({@code hasPaidOrder}) and never re-evaluated later.
	 */
	public void ban(String reason, boolean hasPaidOrder) {
		this.status = CustomerStatus.BANNED;
		this.banReason = Objects.requireNonNull(reason);
		this.hasPaidOrderBeforeBan = hasPaidOrder;
		this.banMode = hasPaidOrder ? CustomerBanMode.PAYMENT_ONLY : CustomerBanMode.CUSTOMER_SCOPE;
	}

	public void unban() {
		this.status = CustomerStatus.ACTIVE;
		this.banReason = null;
		this.hasPaidOrderBeforeBan = null;
		this.banMode = null;
	}

}
