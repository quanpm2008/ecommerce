package com.example.identity_service.common.error;

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

	public ApiException(ErrorCode code, String message) {
		this(code, message, List.of());
	}

	public ApiException(ErrorCode code, String message, List<Map<String, Object>> details) {
		super(message);
		this.code = code;
		this.details = List.copyOf(details);
	}

	public ErrorCode code() {
		return code;
	}

	public List<Map<String, Object>> details() {
		return details;
	}

}
