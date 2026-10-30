package com.example.identity_service.user;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTests {

	@ParameterizedTest
	@ValueSource(strings = { "0912345678", " 0912 345 678 ", "091.234.5678", "091-234-5678", "+84912345678",
			"+84 912 345 678", "84912345678" })
	void acceptedFormatsNormalizeToLocalTenDigits(String input) {
		assertThat(PhoneNumbers.normalize(input)).contains("0912345678");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "091234567", "09123456789", "1912345678", "+85512345678", "09123abcde", "phone" })
	void invalidNumbersAreRejected(String input) {
		assertThat(PhoneNumbers.normalize(input)).isEmpty();
	}

}
