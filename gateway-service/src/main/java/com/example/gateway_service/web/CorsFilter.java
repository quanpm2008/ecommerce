package com.example.gateway_service.web;

import java.util.List;

import com.example.gateway_service.error.ErrorCode;
import com.example.gateway_service.error.ErrorWriter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsProcessor;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.cors.reactive.DefaultCorsProcessor;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * CORS for the SPA (checklist §6, API §2.1, decision 0001), right after the request id and before rate limiting and
 * security:
 * <ul>
 * <li>Only the configured origins ({@code gateway.cors.allowed-origins}), with credentials, so the browser sends
 * identity's refresh cookie.</li>
 * <li>A preflight from an allowed origin is answered here (200, no token needed) and never forwarded.</li>
 * <li>A cross-origin request from any other origin is refused: 403 {@code FORBIDDEN} in the envelope, without
 * {@code Access-Control-Allow-Origin}.</li>
 * <li>The CORS headers are set on the response before anything else writes it, so gateway errors (401, 404, 429, 503)
 * carry them too; services' own CORS headers are dropped ({@code routing.ProxiedResponseHeaders}).</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class CorsFilter implements WebFilter {

	static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE");

	static final List<String> ALLOWED_HEADERS = List.of("Authorization", "Content-Type", "Idempotency-Key",
			RequestIds.HEADER, "Accept-Language", "If-Match");

	static final List<String> EXPOSED_HEADERS = List.of(RequestIds.HEADER, "Retry-After", "Idempotent-Replayed",
			"Location");

	private final CorsConfiguration config;

	private final CorsProcessor processor = new DefaultCorsProcessor();

	private final ErrorWriter errors;

	CorsFilter(EdgeProperties properties, ErrorWriter errors) {
		this.errors = errors;
		this.config = new CorsConfiguration();
		this.config.setAllowedOrigins(properties.cors().allowedOrigins());
		this.config.setAllowedMethods(ALLOWED_METHODS);
		this.config.setAllowedHeaders(ALLOWED_HEADERS);
		this.config.setExposedHeaders(EXPOSED_HEADERS);
		this.config.setAllowCredentials(true);
		this.config.setMaxAge(properties.cors().maxAge());
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		if (!CorsUtils.isCorsRequest(exchange.getRequest())) {
			return chain.filter(exchange);
		}
		if (!processor.process(config, exchange)) {
			return errors.write(exchange, ErrorCode.FORBIDDEN, "Cross-origin requests from this origin are not allowed.");
		}
		if (CorsUtils.isPreFlightRequest(exchange.getRequest())) {
			exchange.getResponse().setStatusCode(HttpStatus.OK);
			return Mono.empty();
		}
		return chain.filter(exchange);
	}

}
