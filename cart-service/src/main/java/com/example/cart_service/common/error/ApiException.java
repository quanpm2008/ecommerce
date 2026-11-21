package com.example.cart_service.common.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thrown by application code to return an error envelope with a stable {@link ErrorCode}.
 */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	private final List<Map<String, Object>> details;

	public ApiException(ErrorCode code) {
		this(code, code.defaultMessage(), List.of());
	}

	public ApiException(ErrorCode code, List<Map<String, Object>> details) {
		this(code, code.defaultMessage(), details);
	}

	public ApiException(ErrorCode code, String message, List<Map<String, Object>> details) {
		super(message);
		this.code = code;
		this.details = List.copyOf(details);
	}

	/** An error about one request field: {@code details: [{ field, issue, message }]}, like bean validation. */
	public static ApiException forField(ErrorCode code, String field, String issue) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("field", field);
		detail.put("issue", issue);
		detail.put("message", code.defaultMessage());
		return new ApiException(code, List.of(detail));
	}

	public ErrorCode code() {
		return code;
	}

	public List<Map<String, Object>> details() {
		return details;
	}

}
