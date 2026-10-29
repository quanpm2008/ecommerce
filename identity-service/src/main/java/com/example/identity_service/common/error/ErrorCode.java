package com.example.identity_service.common.error;

import org.springframework.http.HttpStatus;

/**
 * Error codes from API §4.1. Only codes listed there may be returned; add feature codes here as they are implemented.
 */
public enum ErrorCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Invalid request data"),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Not logged in or token is invalid"),
	FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
	INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "The current state does not allow this action"),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"),
	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "A dependent service is not responding, please try again");

	private final HttpStatus status;
	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus status() {
		return status;
	}

	public String defaultMessage() {
		return defaultMessage;
	}

}
