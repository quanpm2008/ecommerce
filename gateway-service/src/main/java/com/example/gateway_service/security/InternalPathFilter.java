package com.example.gateway_service.security;

import java.nio.charset.StandardCharsets;

import com.example.gateway_service.error.ErrorCode;
import com.example.gateway_service.error.ErrorWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriUtils;

import reactor.core.publisher.Mono;

/**
 * Blocks service-to-service paths from outside (API §2.3, §7.1): a request whose path has a segment {@code internal}, in
 * any case and after percent-decoding, is answered 404 {@code NOT_FOUND} like any unknown path, so it reveals nothing
 * and never reaches a service. That covers {@code /internal/v1/**} itself and any public prefix in front of it
 * ({@code /api/v1/internal/**}, {@code /api/v1/identity/internal/v1/**}); no public path in the API has such a segment.
 * <p>
 * It is the first filter of the security chain, so it runs before any token check (a bad token must not turn the 404
 * into a 401). Spring Security's {@code StrictServerWebExchangeFirewall} runs even earlier and rejects with 400 the
 * encodings that could hide a segment boundary: encoded {@code /}, {@code \}, {@code .} and {@code %}, {@code ;},
 * {@code //} and {@code ../}.
 */
final class InternalPathFilter implements WebFilter {

	private static final Logger log = LoggerFactory.getLogger(InternalPathFilter.class);

	private static final String SEGMENT = "internal";

	private final ErrorWriter errors;

	InternalPathFilter(ErrorWriter errors) {
		this.errors = errors;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		ServerHttpRequest request = exchange.getRequest();
		String path = request.getURI().getRawPath();
		if (isInternal(path)) {
			log.info("Blocked external request to an internal path: {} {}", request.getMethod(), path);
			return errors.write(exchange, ErrorCode.NOT_FOUND);
		}
		return chain.filter(exchange);
	}

	static boolean isInternal(String rawPath) {
		for (String segment : rawPath.split("/")) {
			if (SEGMENT.equalsIgnoreCase(decode(segment))) {
				return true;
			}
		}
		return false;
	}

	private static String decode(String segment) {
		try {
			return UriUtils.decode(segment, StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException malformed) {
			return segment;
		}
	}

}
