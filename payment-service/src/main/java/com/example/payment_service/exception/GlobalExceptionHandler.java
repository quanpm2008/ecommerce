package com.example.payment_service.exception;

import com.example.payment_service.config.RequestIdFilter;
import com.example.payment_service.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private String getRequestId(HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
        if (requestId == null || requestId.isBlank()) {
            requestId = MDC.get(RequestIdFilter.MDC_KEY);
        }
        if (requestId == null || requestId.isBlank()) {
            requestId = request.getHeader(RequestIdFilter.HEADER);
        }
        return requestId;
    }

    private String toUpperSnake(String constraint) {
        if (constraint == null) {
            return null;
        }
        return constraint.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, Object>> details = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("field", error.getField());
            detail.put("issue", toUpperSnake(error.getCode()));
            detail.put("message", error.getDefaultMessage());
            details.add(detail);
        }
        ErrorResponse body = ErrorResponse.of("VALIDATION_ERROR", "Dữ liệu yêu cầu không hợp lệ", details, getRequestId(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of("VALIDATION_ERROR", "Dữ liệu yêu cầu không hợp lệ", List.of(), getRequestId(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of("VALIDATION_ERROR", ex.getMessage(), List.of(), getRequestId(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        String code = "Idempotency-Key".equalsIgnoreCase(ex.getHeaderName()) ? "IDEMPOTENCY_KEY_REQUIRED" : "VALIDATION_ERROR";
        ErrorResponse body = ErrorResponse.of(code, ex.getMessage(), List.of(), getRequestId(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class,
            BadRequestException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        String code = (ex instanceof BadRequestException bre) ? bre.getErrorCode() : "VALIDATION_ERROR";
        ErrorResponse body = ErrorResponse.of(code, ex.getMessage(), List.of(), getRequestId(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), List.of(), getRequestId(request));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler({PaymentNotFoundException.class, RefundNotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of("NOT_FOUND", ex.getMessage(), List.of(), getRequestId(request));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), List.of(), getRequestId(request));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(PaymentConflictException.class)
    public ResponseEntity<ErrorResponse> handlePaymentConflict(PaymentConflictException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), List.of(), getRequestId(request));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(RefundConflictException.class)
    public ResponseEntity<ErrorResponse> handleRefundConflict(RefundConflictException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), List.of(), getRequestId(request));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(RefundRetryNotSafeException.class)
    public ResponseEntity<ErrorResponse> handleRefundRetryNotSafe(RefundRetryNotSafeException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), List.of(), getRequestId(request));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(RefundLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRefundLimitExceeded(RefundLimitExceededException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), List.of(), getRequestId(request));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: ", ex);
        ErrorResponse body = ErrorResponse.of("INTERNAL_ERROR", "Lỗi hệ thống không xác định", List.of(), getRequestId(request));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}

