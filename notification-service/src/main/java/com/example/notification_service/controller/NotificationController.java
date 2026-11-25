package com.example.notification_service.controller;

import com.example.notification_service.common.web.ApiResponse;
import com.example.notification_service.model.NotificationData.Page;
import com.example.notification_service.model.NotificationData.View;
import com.example.notification_service.service.NotificationQueries;
import com.example.notification_service.security.NotificationAccessInterceptor;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications/me")
public class NotificationController {
    private final NotificationQueries queries;

    public NotificationController(NotificationQueries queries) {
        this.queries = queries;
    }

    @GetMapping
    public ApiResponse<List<View>> list(
            @RequestAttribute(NotificationAccessInterceptor.USER_ATTRIBUTE) UUID userId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page result = queries.list(userId, unreadOnly, page, size);
        return new ApiResponse<>(result.items(), new PageMeta(page, size, result.total(),
                (int) ((result.total() + size - 1) / size)));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCount> unreadCount(
            @RequestAttribute(NotificationAccessInterceptor.USER_ATTRIBUTE) UUID userId) {
        return ApiResponse.of(new UnreadCount(queries.unreadCount(userId)));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> read(@RequestAttribute(NotificationAccessInterceptor.USER_ATTRIBUTE) UUID userId,
            @PathVariable UUID notificationId) {
        queries.read(userId, notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll(@RequestAttribute(NotificationAccessInterceptor.USER_ATTRIBUTE) UUID userId) {
        queries.readAll(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> delete(@RequestAttribute(NotificationAccessInterceptor.USER_ATTRIBUTE) UUID userId,
            @PathVariable UUID notificationId) {
        queries.delete(userId, notificationId);
        return ResponseEntity.noContent().build();
    }

    public record PageMeta(int page, int size, long totalItems, int totalPages) {}

    public record UnreadCount(long unreadCount) {}
}
