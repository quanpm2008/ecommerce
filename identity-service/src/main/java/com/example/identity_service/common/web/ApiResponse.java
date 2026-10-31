package com.example.identity_service.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Success envelope (API §3.3): {@code { "data": ... }}, plus {@code meta} for paged lists ({@link PageMeta}) or
 * endpoint-specific metadata such as {@code { maxAddresses }}.
 */
public record ApiResponse<T>(
		T data,
		@JsonInclude(JsonInclude.Include.NON_NULL) Object meta) {

	public static <T> ApiResponse<T> of(T data) {
		return new ApiResponse<>(data, null);
	}

	public static <T> ApiResponse<T> of(T data, Object meta) {
		return new ApiResponse<>(data, meta);
	}

	public static <T> ApiResponse<List<T>> page(Page<T> page) {
		return new ApiResponse<>(page.getContent(), PageMeta.from(page));
	}

}
