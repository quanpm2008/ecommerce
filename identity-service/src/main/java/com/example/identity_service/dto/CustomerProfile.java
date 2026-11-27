package com.example.identity_service.dto;

import com.example.identity_service.entity.Customer;
import com.example.identity_service.entity.CustomerStatus;
import com.example.identity_service.entity.AccessMode;

import java.time.Instant;
import java.util.UUID;

/**
 * API {@code CustomerProfile}: {@code accessMode} is the account's current one (FULL unless banned, G-14);
 * {@code profileComplete} means checkout prerequisites are met (phone + address).
 */
public record CustomerProfile(UUID customerId, CustomerStatus status, String banReason, AccessMode accessMode,
		boolean profileComplete, Instant createdAt) {

	public static CustomerProfile of(Customer customer, boolean profileComplete) {
		return new CustomerProfile(customer.getCustomerId(), customer.getStatus(), customer.getBanReason(),
				customer.getUser().getAccessMode(), profileComplete, customer.getCreatedAt());
	}

}
