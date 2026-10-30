package com.example.identity_service.account;

import com.example.identity_service.customer.Customer;
import com.example.identity_service.customer.CustomerBanMode;
import com.example.identity_service.customer.CustomerStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * API {@code CustomerProfile}. {@code accessMode} spells out G-14: PAYMENT_BLOCKED keeps old orders usable,
 * BLOCKED stops all customer APIs. {@code profileComplete} means checkout prerequisites are met (phone + address).
 */
public record CustomerProfile(UUID customerId, CustomerStatus status, String banReason, boolean paymentBlocked,
		AccessMode accessMode, boolean profileComplete, Instant createdAt) {

	public enum AccessMode {
		FULL, PAYMENT_BLOCKED, BLOCKED
	}

	static CustomerProfile of(Customer customer, boolean profileComplete) {
		boolean banned = customer.getStatus() == CustomerStatus.BANNED;
		AccessMode mode = !banned ? AccessMode.FULL
				: customer.getBanMode() == CustomerBanMode.PAYMENT_ONLY ? AccessMode.PAYMENT_BLOCKED : AccessMode.BLOCKED;
		return new CustomerProfile(customer.getCustomerId(), customer.getStatus(), customer.getBanReason(), banned,
				mode, profileComplete, customer.getCreatedAt());
	}

}
