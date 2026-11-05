package com.example.identity_service.user;

import java.nio.charset.StandardCharsets;

/**
 * G-07: at least 8 characters with an uppercase letter, a lowercase letter and a digit. The 72-byte cap is BCrypt's
 * input limit, not a spec rule.
 */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 8;

	public static final int MAX_BYTES = 72;

	private PasswordPolicy() {
	}

	public static boolean isSatisfiedBy(String password) {
		return password != null
				&& password.length() >= MIN_LENGTH
				&& password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES
				&& password.chars().anyMatch(Character::isUpperCase)
				&& password.chars().anyMatch(Character::isLowerCase)
				&& password.chars().anyMatch(Character::isDigit);
	}

}
