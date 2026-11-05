package com.example.identity_service.customer;

import java.time.Instant;
import java.util.UUID;

/**
 * API {@code Address}. Province and ward names come from the region source and are null when it is unavailable.
 */
public record Address(UUID addressId, String recipientName, String recipientPhone, String provinceCode,
		String provinceName, String wardCode, String wardName, String addressDetail, Instant lastUsedAt,
		boolean isDefault, Instant createdAt) {
}
