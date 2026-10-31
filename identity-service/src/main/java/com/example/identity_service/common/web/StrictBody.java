package com.example.identity_service.common.web;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A JSON object body checked against an allowlist: any other field is a 400 {@code VALIDATION_ERROR}
 * ({@code issue: UNKNOWN_FIELD}) instead of being ignored, so clients cannot mass-assign role, status or owner fields.
 * Also tells "absent" apart from "null", which PATCH needs.
 */
public final class StrictBody {

	private final Map<String, Object> fields;

	private StrictBody(Map<String, Object> fields) {
		this.fields = fields;
	}

	public static StrictBody of(Map<String, Object> body, Set<String> allowed) {
		List<Map<String, Object>> unknown = new ArrayList<>();
		for (String field : body.keySet()) {
			if (!allowed.contains(field)) {
				unknown.add(detail(field, "UNKNOWN_FIELD", "This field cannot be set here"));
			}
		}
		if (!unknown.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), unknown);
		}
		return new StrictBody(body);
	}

	public boolean has(String field) {
		return fields.containsKey(field);
	}

	/** A required, non-blank string of at most {@code maxLength} characters, stripped. */
	public String text(String field, int maxLength) {
		String value = nullableText(field, maxLength);
		if (value == null || value.isEmpty()) {
			throw invalid(field, "NOT_BLANK", "must not be blank");
		}
		return value;
	}

	/** A string of at most {@code maxLength} characters, stripped; null or blank gives null. */
	public String nullableText(String field, int maxLength) {
		Object value = fields.get(field);
		if (value == null) {
			return null;
		}
		if (!(value instanceof String text)) {
			throw invalid(field, "NOT_A_STRING", "must be a string");
		}
		String stripped = text.strip();
		if (stripped.length() > maxLength) {
			throw invalid(field, "SIZE", "must be at most " + maxLength + " characters");
		}
		return stripped.isEmpty() ? null : stripped;
	}

	private static ApiException invalid(String field, String issue, String message) {
		return new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
				List.of(detail(field, issue, message)));
	}

	private static Map<String, Object> detail(String field, String issue, String message) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("field", field);
		detail.put("issue", issue);
		detail.put("message", message);
		return detail;
	}

}
