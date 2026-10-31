package com.example.identity_service.common.web;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrictBodyTests {

	@Test
	void unknownFieldsAreListedInTheError() {
		assertThatThrownBy(() -> StrictBody.of(Map.of("fullName", "A", "role", "ADMIN"), Set.of("fullName")))
				.isInstanceOfSatisfying(ApiException.class, ex -> {
					assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
					assertThat(ex.details()).singleElement()
							.satisfies(d -> assertThat(d).containsEntry("field", "role").containsEntry("issue", "UNKNOWN_FIELD"));
				});
	}

	@Test
	void absentNullAndValuesAreToldApart() {
		Map<String, Object> body = new HashMap<>();
		body.put("description", null);
		body.put("name", "  Shop  ");
		StrictBody fields = StrictBody.of(body, Set.of("description", "name", "phone"));

		assertThat(fields.has("phone")).isFalse();
		assertThat(fields.has("description")).isTrue();
		assertThat(fields.nullableText("description", 10)).isNull();
		assertThat(fields.text("name", 10)).isEqualTo("Shop");
	}

	@Test
	void requiredTextRejectsNullBlankWrongTypeAndTooLong() {
		Map<String, Object> body = new HashMap<>();
		body.put("a", null);
		body.put("b", "   ");
		body.put("c", 42);
		body.put("d", "abcdef");
		StrictBody fields = StrictBody.of(body, Set.of("a", "b", "c", "d"));

		assertIssue(() -> fields.text("a", 10), "NOT_BLANK");
		assertIssue(() -> fields.text("b", 10), "NOT_BLANK");
		assertIssue(() -> fields.text("c", 10), "NOT_A_STRING");
		assertIssue(() -> fields.text("d", 5), "SIZE");
	}

	private static void assertIssue(Runnable call, String issue) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.details()).singleElement().satisfies(d -> assertThat(d).containsEntry("issue", issue)));
	}

}
