package com.example.gateway_service.error;

import com.example.gateway_service.web.RequestIds;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes an error the gateway produces itself as the API §3.3 envelope, with the request's {@code X-Request-Id}. The
 * one place every gateway-side failure (unknown path, blocked {@code /internal} path, rejected request, bad token,
 * downstream unavailable) goes through, so they all look the same.
 */
@Component
public class ErrorWriter {

	private final JsonMapper json;

	ErrorWriter(JsonMapper json) {
		this.json = json;
	}

	public Mono<Void> write(ServerWebExchange exchange, ErrorCode code) {
		return write(exchange, code.status().value(), code, code.defaultMessage());
	}

	public Mono<Void> write(ServerWebExchange exchange, ErrorCode code, String message) {
		return write(exchange, code.status().value(), code, message);
	}

	/** For a status without its own code (e.g. 405), which then carries the generic 4xx / 5xx code. */
	public Mono<Void> write(ServerWebExchange exchange, int status, ErrorCode code, String message) {
		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(HttpStatusCode.valueOf(status));
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		byte[] body = json.writeValueAsBytes(
				ErrorResponse.of(code, message, RequestIds.current(exchange)));
		return response.writeWith(Mono.fromSupplier(() -> response.bufferFactory().wrap(body)));
	}

}
