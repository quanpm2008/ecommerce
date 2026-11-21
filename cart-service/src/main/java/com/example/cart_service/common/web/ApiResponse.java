package com.example.cart_service.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Success envelope (API §3.3): {@code { "data": ... }}, plus {@code meta} when an endpoint has one. Also used to read
 * the same envelope from other services.
 */
public record ApiResponse<T>(T data, @JsonInclude(JsonInclude.Include.NON_NULL) Object meta) {

	public static <T> ApiResponse<T> of(T data) {
		return new ApiResponse<>(data, null);
	}

}
