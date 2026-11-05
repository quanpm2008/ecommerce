package com.example.identity_service.auth;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class UniqueConflictsTests {

	@ParameterizedTest
	@CsvSource({ "uq_users_email, EMAIL_ALREADY_USED, email",
			"uq_users_phone_number, PHONE_ALREADY_USED, phoneNumber",
			"uq_sellers_seller_name_active, SELLER_NAME_ALREADY_USED, sellerName",
			"uq_sellers_contact_phone_active, PHONE_ALREADY_USED, contactPhone" })
	void raceOnAUniqueIndexBecomesTheMatchingConflict(String constraint, ErrorCode code, String field) {
		RuntimeException translated = UniqueConflicts.translate(violation(constraint));

		assertThat(translated).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.code()).isEqualTo(code);
			assertThat(ex.details()).singleElement().satisfies(d -> assertThat(d).containsEntry("field", field));
		});
	}

	@Test
	void otherViolationsAreLeftAlone() {
		DataIntegrityViolationException other = violation("ck_users_auth_method");

		assertThat(UniqueConflicts.translate(other)).isSameAs(other);
	}

	private static DataIntegrityViolationException violation(String constraint) {
		return new DataIntegrityViolationException("could not execute statement",
				new SQLException("ERROR: duplicate key value violates unique constraint \"" + constraint + "\""));
	}

}
