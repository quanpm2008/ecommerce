package com.example.identity_service.user;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Vietnamese phone numbers in the stored form {@code 0xxxxxxxxx} (API §6.1). Accepts spaces, dots, dashes and the
 * {@code +84} / {@code 84} country prefix.
 */
public final class PhoneNumbers {

	private static final Pattern NORMALIZED = Pattern.compile("0[0-9]{9}");

	private PhoneNumbers() {
	}

	/** The normalized number, or empty if it is not a valid Vietnamese number. */
	public static Optional<String> normalize(String input) {
		if (input == null) {
			return Optional.empty();
		}
		String digits = input.strip().replaceAll("[\\s.\\-()]", "");
		if (digits.startsWith("+84")) {
			digits = "0" + digits.substring(3);
		}
		else if (digits.startsWith("84") && digits.length() == 11) {
			digits = "0" + digits.substring(2);
		}
		return NORMALIZED.matcher(digits).matches() ? Optional.of(digits) : Optional.empty();
	}

}
