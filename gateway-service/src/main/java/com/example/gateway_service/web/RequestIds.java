package com.example.gateway_service.web;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

/**
 * The request's {@code X-Request-Id} (API §3.10). The gateway owns it: a single id the client sent in identity-service's
 * safe format is kept, anything else (missing, unsafe, sent twice) is replaced by a generated UUID
 * ({@link RequestIdFilter}). The id is then on the exchange, the forwarded request, the response, the MDC
 * ({@value #MDC_KEY}) and {@code error.requestId}.
 */
public final class RequestIds {

	public static final String HEADER = "X-Request-Id";

	/** MDC key, the same as identity-service's, so one id finds a request in every log. */
	public static final String MDC_KEY = "requestId";

	static final String ATTRIBUTE = RequestIds.class.getName() + ".id";

	private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

	private RequestIds() {
	}

	/** The request's id: the one {@link RequestIdFilter} settled on, else a safe id the client sent, else {@code null}. */
	public static String current(ServerWebExchange exchange) {
		return exchange.getAttribute(ATTRIBUTE) instanceof String id ? id : sent(exchange.getRequest());
	}

	/** Runs {@code action} (a log statement) with the request's id in the MDC, for code outside the request's Reactor context. */
	public static void withMdc(ServerWebExchange exchange, Runnable action) {
		String id = current(exchange);
		if (id == null) {
			action.run();
			return;
		}
		try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, id)) {
			action.run();
		}
	}

	/** A single id sent by the client in the safe format, or {@code null}. */
	static String sent(ServerHttpRequest request) {
		List<String> values = request.getHeaders().getOrEmpty(HEADER);
		return values.size() == 1 && SAFE_ID.matcher(values.getFirst()).matches() ? values.getFirst() : null;
	}

	static String generate() {
		return UUID.randomUUID().toString();
	}

}
