package com.example.gateway_service.web;

import java.util.regex.Pattern;

import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * The request's {@code X-Request-Id} (API §3.10). Only ids in identity-service's safe format are used; anything else
 * counts as missing. Generating an id when none is sent comes with checklist §4.
 */
public final class RequestIds {

	public static final String HEADER = "X-Request-Id";

	private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

	private RequestIds() {
	}

	/** The safe id sent with the request, or {@code null}. */
	public static String current(ServerHttpRequest request) {
		String id = request.getHeaders().getFirst(HEADER);
		return id != null && SAFE_ID.matcher(id).matches() ? id : null;
	}

}
