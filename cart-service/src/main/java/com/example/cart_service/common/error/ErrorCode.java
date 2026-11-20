package com.example.cart_service.common.error;

import org.springframework.http.HttpStatus;

/**
 * Error codes Cart can return, with their HTTP status (API §3.4 and §4.1). The name is the stable {@code error.code}.
 */
public enum ErrorCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Invalid request data"),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Not logged in or token is invalid"),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Access token has expired"),
	TOKEN_STALE(HttpStatus.UNAUTHORIZED, "Access token permissions are out of date; refresh the token"),
	FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
	ACCOUNT_BANNED(HttpStatus.FORBIDDEN, "A banned account cannot use the cart"),
	ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "This account is disabled"),
	PROFILE_REQUIRED(HttpStatus.FORBIDDEN, "A customer profile is required"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
	PRODUCT_NOT_SELLABLE(HttpStatus.UNPROCESSABLE_CONTENT, "The product is not available for sale"),
	QUANTITY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT,
			"At most 99 units per line and 50 different products per cart"),
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
