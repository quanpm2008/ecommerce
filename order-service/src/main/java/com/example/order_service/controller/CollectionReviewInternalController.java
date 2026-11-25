package com.example.order_service.controller;

import com.example.order_service.exception.OrderApiExceptionHandler;
import com.example.order_service.security.OrderAuthorization;
import com.example.order_service.service.CollectionReviewService;
import com.example.order_service.service.OrderLifecycleService.Rejected;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/orders")
class CollectionReviewInternalController {
    private final OrderAuthorization authorization;
    private final CollectionReviewService reviews;
    private final OrderApiExceptionHandler errors;
    private final DefaultBearerTokenResolver tokens = new DefaultBearerTokenResolver();

    CollectionReviewInternalController(OrderAuthorization authorization, CollectionReviewService reviews,
            OrderApiExceptionHandler errors) {
        this.authorization = authorization;
        this.reviews = reviews;
        this.errors = errors;
    }

    @GetMapping("/collection-reviews")
    Map<String, Object> reviews(@RequestParam(required = false) UUID checkoutSessionId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        authorization.authorizeAdmin(tokens.resolve(request), false);
        return Map.of("data", reviews.reviews(checkoutSessionId, page, size));
    }

    @PostMapping("/checkout-sessions/{id}/mark-handled")
    Map<String, Object> mark(@PathVariable UUID id, @RequestBody Note body, HttpServletRequest request) {
        var admin = authorization.authorizeAdmin(tokens.resolve(request), true);
        return Map.of("data", reviews.mark(id, admin.userId(), body.note(), request.getHeader("X-Request-Id")));
    }

    @ExceptionHandler(Rejected.class)
    void rejected(Rejected failure, HttpServletRequest request, HttpServletResponse response) throws IOException {
        errors.write(request, response, failure.status(), failure.getMessage(), failure.getMessage());
    }

    @ExceptionHandler(OrderAuthorization.Rejected.class)
    void rejected(OrderAuthorization.Rejected failure, HttpServletRequest request, HttpServletResponse response) throws IOException {
        errors.write(request, response, failure.status(), failure.getMessage(), failure.getMessage());
    }

    @ExceptionHandler(OAuth2AuthenticationException.class)
    void rejected(OAuth2AuthenticationException failure, HttpServletRequest request, HttpServletResponse response) throws IOException {
        errors.write(request, response, 401, "UNAUTHENTICATED", "UNAUTHENTICATED");
    }

    @ExceptionHandler({DataAccessException.class, PersistenceException.class})
    void unavailable(HttpServletRequest request, HttpServletResponse response) throws IOException {
        errors.write(request, response, 503, "SERVICE_UNAVAILABLE", "SERVICE_UNAVAILABLE");
    }

    record Note(String note) {}
}
