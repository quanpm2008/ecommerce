package com.example.cart_service.client;

import java.util.List;
import java.util.UUID;

/**
 * Identity's current authorization context for a user token (API §7.1). {@code roles} and {@code permissions} are
 * empty unless the session is active and the user ACTIVE; profile statuses are always reported.
 */
public record AuthorizationContext(UUID userId, String userStatus, long authzVersion, List<String> roles,
		List<String> permissions, CustomerAccess customerProfile, boolean sessionActive) {

	public AuthorizationContext {
		roles = roles == null ? List.of() : List.copyOf(roles);
		permissions = permissions == null ? List.of() : List.copyOf(permissions);
	}

	/** {@code status}: ACTIVE or BANNED. */
	public record CustomerAccess(String status, String accessMode, boolean paymentBlocked) {
	}

}
