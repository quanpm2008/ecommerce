package com.example.identity_service.auth;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;

/**
 * Turns a unique-index violation into the matching 409. The services check first; this covers two registrations
 * racing past those checks.
 */
final class UniqueConflicts {

	private record Conflict(ErrorCode code, String field) {
	}

	private static final Map<String, Conflict> BY_CONSTRAINT = Map.of(
			"uq_users_email", new Conflict(ErrorCode.EMAIL_ALREADY_USED, "email"),
			"uq_users_phone_number", new Conflict(ErrorCode.PHONE_ALREADY_USED, "phoneNumber"),
			"uq_sellers_seller_name_active", new Conflict(ErrorCode.SELLER_NAME_ALREADY_USED, "sellerName"),
			"uq_sellers_contact_phone_active", new Conflict(ErrorCode.PHONE_ALREADY_USED, "contactPhone"));

	private UniqueConflicts() {
	}

	static RuntimeException translate(DataIntegrityViolationException ex) {
		String message = String.valueOf(ex.getMostSpecificCause().getMessage());
		return BY_CONSTRAINT.entrySet().stream()
				.filter(entry -> message.contains(entry.getKey()))
				.findFirst()
				.<RuntimeException>map(entry -> ApiException.forField(entry.getValue().code(), entry.getValue().field(),
						"DUPLICATE"))
				.orElse(ex);
	}

}
