package com.example.gateway_service.routing;

import com.example.gateway_service.error.ErrorCode;
import com.example.gateway_service.error.ErrorResponse;
import com.example.gateway_service.web.RequestIds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Turns a failure to get a downstream response into a gateway error. No instance registered (the load-balancer filter
 * throws a 503), connection refused, or no answer within {@code spring.http.clients.read-timeout} → 503
 * {@code SERVICE_UNAVAILABLE}, so the client may retry; anything else → 500 {@code INTERNAL_ERROR}.
 */
@Component
class DownstreamErrors {

	private static final Logger log = LoggerFactory.getLogger(DownstreamErrors.class);

	ServerResponse toResponse(Throwable failure, ServerRequest request) {
		ErrorCode code;
		if (isUnavailable(failure)) {
			code = ErrorCode.SERVICE_UNAVAILABLE;
			log.warn("{} {}: downstream unavailable ({})", request.method(), request.path(), failure.toString());
		}
		else {
			code = ErrorCode.INTERNAL_ERROR;
			log.error("{} {}: proxying failed", request.method(), request.path(), failure);
		}
		return ServerResponse.status(code.status())
				.contentType(MediaType.APPLICATION_JSON)
				.body(ErrorResponse.of(code, code.defaultMessage(), RequestIds.current(request.servletRequest())));
	}

	private static boolean isUnavailable(Throwable failure) {
		return failure instanceof ResourceAccessException
				|| failure instanceof HttpStatusCodeException status
						&& status.getStatusCode().isSameCodeAs(HttpStatus.SERVICE_UNAVAILABLE);
	}

}
