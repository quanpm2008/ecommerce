package com.example.gateway_service.error;

import java.net.ConnectException;

import com.example.gateway_service.web.RequestIds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import io.netty.channel.ConnectTimeoutException;
import reactor.core.publisher.Mono;

/**
 * Every exception that reaches the web layer becomes the API §3.3 envelope; Spring Boot's default error JSON and
 * Whitelabel page are never used. Runs before Boot's own handler (order -1).
 * <ul>
 * <li>No response from a service: no instance registered, connection refused or timed out, or no answer within
 * {@code spring.cloud.gateway.server.webflux.httpclient.response-timeout} → 503 {@code SERVICE_UNAVAILABLE}, so the
 * client may retry.</li>
 * <li>Errors with a status (no route → 404, a method the actuator doesn't take → 405, ...) → that status and its code
 * ({@link ErrorCode#forStatus}).</li>
 * <li>Anything else → 500 {@code INTERNAL_ERROR}, logged; the body never shows the exception.</li>
 * </ul>
 * Downstream responses, error bodies included, are not exceptions and pass through untouched.
 */
@Component
@Order(-2)
class GatewayErrorHandler implements ErrorWebExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

	private final ErrorWriter errors;

	GatewayErrorHandler(ErrorWriter errors) {
		this.errors = errors;
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
		if (exchange.getResponse().isCommitted()) {
			return Mono.error(failure);
		}
		ServerHttpRequest request = exchange.getRequest();
		// Error handlers run outside the request's Reactor context, so the request id is put in the MDC by hand.
		if (isUnavailable(failure)) {
			RequestIds.withMdc(exchange, () -> log.warn("{} {}: downstream unavailable ({})", request.getMethod(),
					request.getPath(), failure.toString()));
			return errors.write(exchange, ErrorCode.SERVICE_UNAVAILABLE);
		}
		if (failure instanceof ResponseStatusException withStatus && withStatus.getStatusCode().value() < 500) {
			int status = withStatus.getStatusCode().value();
			ErrorCode code = ErrorCode.forStatus(status);
			String message = code.status().value() == status ? code.defaultMessage() : reasonPhrase(status);
			return errors.write(exchange, status, code, message);
		}
		RequestIds.withMdc(exchange, () -> log.error("{} {}: request failed", request.getMethod(), request.getPath(),
				failure));
		return errors.write(exchange, ErrorCode.INTERNAL_ERROR);
	}

	private static boolean isUnavailable(Throwable failure) {
		if (failure instanceof NotFoundException
				|| failure instanceof ResponseStatusException withStatus
						&& (withStatus.getStatusCode().isSameCodeAs(HttpStatus.SERVICE_UNAVAILABLE)
								|| withStatus.getStatusCode().isSameCodeAs(HttpStatus.GATEWAY_TIMEOUT))) {
			return true;
		}
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConnectException || cause instanceof ConnectTimeoutException
					|| cause instanceof TimeoutException) {
				return true;
			}
		}
		return false;
	}

	private static String reasonPhrase(int status) {
		HttpStatus known = HttpStatus.resolve(status);
		return known != null ? known.getReasonPhrase() + "." : "The request failed.";
	}

}
