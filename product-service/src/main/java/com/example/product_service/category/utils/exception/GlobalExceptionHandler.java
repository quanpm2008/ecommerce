package com.example.product_service.category.utils.exception;

import com.example.product_service.category.exception.CategoryInUseException;
import com.example.product_service.category.exception.CategoryNameDuplicateException;
import com.example.product_service.category.utils.dto.ApiErrorResponse;
import com.example.product_service.category.utils.dto.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private String getRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId;
    }

    @ExceptionHandler(CategoryNameDuplicateException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryNameDuplicateException(
            CategoryNameDuplicateException e,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.builder()
                .code("CATEGORY_NAME_DUPLICATE")
                .message(e.getMessage())
                .details(List.of())
                .requestId(getRequestId(request))
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(error));
    }

    @ExceptionHandler(CategoryInUseException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryInUseException(
            CategoryInUseException e,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.builder()
                .code("CATEGORY_IN_USE")
                .message(e.getMessage())
                .details(List.of())
                .requestId(getRequestId(request))
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(error));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFoundException(
            EntityNotFoundException e,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.builder()
                .code("NOT_FOUND")
                .message(e.getMessage())
                .details(List.of())
                .requestId(getRequestId(request))
                .build();

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e,
            HttpServletRequest request
    ) {
        List<Map<String, Object>> details = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> Map.<String, Object>of(
                        "field", fieldError.getField(),
                        "issue", fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "INVALID"
                ))
                .toList();

        ErrorResponse error = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Dữ liệu đầu vào không hợp lệ")
                .details(details)
                .requestId(getRequestId(request))
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(error));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException e,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message(e.getMessage())
                .details(List.of())
                .requestId(getRequestId(request))
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(error));
    }
}