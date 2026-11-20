package com.example.product_service.media.common.exception;

import com.example.product_service.common.response.ApiErrorResponse;
import com.example.product_service.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class MediaExceptionHandler {

    @ExceptionHandler(value = MediaInvalidException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaInvalid(MediaInvalidException e, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        ErrorResponse errorBody = ErrorResponse.builder()
                .code("MEDIA_INVALID")
                .message(e.getMessage())
                .details(e.getDetails())
                .requestId(requestId)
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorResponse.builder().error(errorBody).build());
    }
}
