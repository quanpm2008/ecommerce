package com.example.product_service.product.utils.exception;

import com.example.product_service.category.exception.CategoryInUseException;
import com.example.product_service.category.exception.CategoryNameDuplicateException;
import com.example.product_service.category.exception.CategoryNotAvailableException;
import com.example.product_service.common.response.ApiErrorResponse;
import com.example.product_service.common.response.ErrorResponse;
import com.example.product_service.product.exception.*;
import com.example.product_service.review.exception.ReviewAlreadyExistsException;
import com.example.product_service.review.exception.ReviewNotAllowedException;
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
public class GlobalExceptionHandle {

    private String getRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId;
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status, String code, String message, HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
                .code(code)
                .message(message)
                .details(List.of())
                .requestId(getRequestId(request))
                .build();

        return ResponseEntity.status(status).body(new ApiErrorResponse(error));
    }

    @ExceptionHandler(CategoryNameDuplicateException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryNameDuplicate(
            CategoryNameDuplicateException e, HttpServletRequest req) {
        return buildResponse(HttpStatus.CONFLICT, "CATEGORY_NAME_DUPLICATE", e.getMessage(), req);
    }

    @ExceptionHandler(CategoryInUseException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryInUse(
            CategoryInUseException e, HttpServletRequest req) {
        return buildResponse(HttpStatus.CONFLICT, "CATEGORY_IN_USE", e.getMessage(), req);
    }

    @ExceptionHandler(CategoryNotAvailableException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryNotAvailable(
            CategoryNotAvailableException e, HttpServletRequest req) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "CATEGORY_NOT_AVAILABLE", e.getMessage(), req);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProductNotFound(
            ProductNotFoundException e, HttpServletRequest req) {
        return buildResponse(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", e.getMessage(), req);
    }

    @ExceptionHandler(ProductRemovedByAdminException.class)
    public ResponseEntity<ApiErrorResponse> handleProductRemoved(
            ProductRemovedByAdminException e, HttpServletRequest req) {
        return buildResponse(HttpStatus.CONFLICT, "PRODUCT_REMOVED_BY_ADMIN", e.getMessage(), req);
    }

    @ExceptionHandler(RestoreWindowExpiredException.class)
    public ResponseEntity<ApiErrorResponse> handleRestoreExpired(
            RestoreWindowExpiredException e, HttpServletRequest req) {
        return buildResponse(HttpStatus.CONFLICT, "RESTORE_WINDOW_EXPIRED", e.getMessage(), req);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidStateTransition(
            InvalidStateTransitionException e, HttpServletRequest req) {
        return buildResponse(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", e.getMessage(), req);
    }

    @ExceptionHandler(UnauthorizedProductAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorizedProduct(
            UnauthorizedProductAccessException e, HttpServletRequest req) {
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage(), req);
    }

    @ExceptionHandler(ProductMediaInvalidException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaInvalid(
            ProductMediaInvalidException e, HttpServletRequest req) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "MEDIA_INVALID", e.getMessage(), req);
    }

    @ExceptionHandler(ReviewAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleReviewAlreadyExists(
            ReviewAlreadyExistsException e, HttpServletRequest req) {
        return buildResponse(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS", e.getMessage(), req);
    }

    @ExceptionHandler(ReviewNotAllowedException.class)
    public ResponseEntity<ApiErrorResponse> handleReviewNotAllowed(
            ReviewNotAllowedException e, HttpServletRequest req) {
        return buildResponse(HttpStatus.FORBIDDEN, "REVIEW_NOT_ALLOWED", e.getMessage(), req);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFound(
            EntityNotFoundException e, HttpServletRequest req) {
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpServletRequest req) {
        List<Map<String, Object>> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.<String, Object>of(
                        "field", f.getField(),
                        "issue", f.getDefaultMessage() != null ? f.getDefaultMessage() : "INVALID"
                ))
                .toList();

        ErrorResponse error = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Dữ liệu đầu vào không hợp lệ")
                .details(details)
                .requestId(getRequestId(req))
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse(error));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException e, HttpServletRequest req) {
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage(), req);
    }
}
