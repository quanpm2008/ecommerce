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
		return new CustomerProfile(customer.getCustomerId(), customer.getStatus(), customer.getBanReason(),
				customer.getStatus() == CustomerStatus.BANNED, accessModeOf(customer), profileComplete,
				customer.getCreatedAt());
	}

	/** G-14: a banned customer with a paid order before the ban keeps old orders; one without loses everything. */
	public static AccessMode accessModeOf(Customer customer) {
		if (customer.getStatus() != CustomerStatus.BANNED) {
			return AccessMode.FULL;
		}
		return customer.getBanMode() == CustomerBanMode.PAYMENT_ONLY ? AccessMode.PAYMENT_BLOCKED : AccessMode.BLOCKED;
	}

}
