package com.example.cart_service.common.error;

import com.example.cart_service.common.web.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns every exception raised in Spring MVC (controllers and interceptors) into the API §3.3 error envelope.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
		return respond(ex.code(), ex.getMessage(), ex.details(), new HttpHeaders(), ex.code().status(), request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
		ApiException error = ApiException.forField(ErrorCode.VALIDATION_ERROR, ex.getName(), "INVALID_FORMAT");
		return handleApiException(error, request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
		log.error("Unhandled exception", ex);
		ErrorCode code = ErrorCode.INTERNAL_ERROR;
		return respond(code, code.defaultMessage(), List.of(), new HttpHeaders(), code.status(), request);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, Object>> details = ex.getBindingResult().getFieldErrors().stream()
				.map(GlobalExceptionHandler::fieldDetail)
				.toList();
		ErrorCode code = ErrorCode.VALIDATION_ERROR;
		return respond(code, code.defaultMessage(), details, headers, code.status(), request);
	}

	/** Standard Spring MVC exceptions (malformed body, unknown path, wrong method, ...) keep their HTTP status. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ErrorCode code = switch (statusCode.value()) {
			case 401 -> ErrorCode.UNAUTHENTICATED;
			case 403 -> ErrorCode.FORBIDDEN;
			case 404 -> ErrorCode.NOT_FOUND;
			case 429 -> ErrorCode.RATE_LIMITED;
			case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
			default -> statusCode.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.VALIDATION_ERROR;
		};
		return respond(code, code.defaultMessage(), List.of(), headers, statusCode, request);
	}

	private static Map<String, Object> fieldDetail(FieldError error) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("field", error.getField());
		detail.put("issue", error.getCode() == null ? null
				: error.getCode().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase());
		detail.put("message", error.getDefaultMessage());
		return detail;
	}

	private static ResponseEntity<Object> respond(ErrorCode code, String message, List<Map<String, Object>> details,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
		ErrorResponse body = ErrorResponse.of(code, message, details, requestId != null ? requestId.toString() : null);
		return ResponseEntity.status(status).headers(headers).body(body);
	}

}
