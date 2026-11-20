package com.example.order_service.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class OrderApiExceptionHandler {

	private final JsonMapper json;

	public OrderApiExceptionHandler(JsonMapper json) {
		this.json = json;
	}

	@ExceptionHandler({ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class})
	void invalidArgument(HttpServletRequest request, HttpServletResponse response) throws IOException {
		write(request, response, 400, "VALIDATION_ERROR", "Tham số yêu cầu không hợp lệ");
	}

	@ExceptionHandler(ResponseStatusException.class)
	void invalidRequest(ResponseStatusException exception, HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		if (exception.getStatusCode().value() != 400) {
			throw exception;
		}
		invalidArgument(request, response);
	}

	public void write(HttpServletRequest request, HttpServletResponse response, int status, String code,
			String message) throws IOException {
		String requestId = request.getHeader("X-Request-Id");
		if (requestId == null || requestId.isBlank()) {
			requestId = UUID.randomUUID().toString();
		}
		response.setStatus(status);
		response.setHeader("X-Request-Id", requestId);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		json.writeValue(response.getOutputStream(),
				new ErrorEnvelope(new ErrorBody(code, message, List.of(), requestId)));
	}

	private record ErrorEnvelope(ErrorBody error) {
	}

	private record ErrorBody(String code, String message, List<?> details, String requestId) {
	}
}
