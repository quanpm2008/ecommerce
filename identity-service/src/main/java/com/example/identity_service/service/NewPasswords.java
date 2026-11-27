package com.example.identity_service.service;

import com.example.identity_service.exception.ApiException;
import com.example.identity_service.exception.ErrorCode;

/**
 * Checks shared by every endpoint that sets a password.
 */
final class NewPasswords {

	private NewPasswords() {
	}

	/** {@code field} names the new password in the request, for {@code details}. */
	static void check(String password, String confirmPassword, String field) {
		if (!password.equals(confirmPassword)) {
			throw ApiException.forField(ErrorCode.VALIDATION_ERROR, "confirmPassword", "MISMATCH");
		}
		if (!PasswordPolicy.isSatisfiedBy(password)) {
			throw ApiException.forField(ErrorCode.PASSWORD_POLICY_VIOLATION, field, "POLICY");
		}
	}

}
