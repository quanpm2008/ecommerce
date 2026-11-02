package com.example.gateway_service.error;

import com.example.gateway_service.web.RequestIds;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's {@code /error} page, so every error the gateway produces itself (no route, method not allowed,
 * rejected request, uncaught exception) uses the API §3.3 envelope instead of the default JSON or Whitelabel page.
 * <p>
 * Calling {@code /error} directly carries no error status and is answered as an unknown path.
 */
@RestController
class GatewayErrorController implements ErrorController {

	@RequestMapping("${server.error.path:/error}")
	ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
		Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		int status = attribute instanceof Integer value && value >= 400 ? value : HttpStatus.NOT_FOUND.value();
		ErrorCode code = ErrorCode.forStatus(status);
		String message = code.status().value() == status ? code.defaultMessage() : reasonPhrase(status);
		return ResponseEntity.status(status)
				.contentType(MediaType.APPLICATION_JSON)
				.body(ErrorResponse.of(code, message, RequestIds.current(request)));
	}

	private static String reasonPhrase(int status) {
		HttpStatus known = HttpStatus.resolve(status);
		return known != null ? known.getReasonPhrase() + "." : "The request failed.";
	}

}
