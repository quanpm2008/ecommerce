package com.example.notification_service.controller;

import com.example.notification_service.common.web.ApiResponse;
import com.example.notification_service.service.NotificationApplication;
import com.example.notification_service.model.NotificationData.CreateRequest;
import com.example.notification_service.model.NotificationData.CreationResult;
import com.example.notification_service.security.InternalAccessInterceptor;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/notifications")
public class InternalNotificationController {
    private final NotificationApplication application;

    public InternalNotificationController(NotificationApplication application) {
        this.application = application;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreatedNotifications>> create(
            @RequestAttribute(InternalAccessInterceptor.PRODUCER_ATTRIBUTE) String producer,
            @RequestBody CreateRequest body) {
        CreationResult result = application.create(producer, body);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.of(new CreatedNotifications(result.notificationIds(), "PENDING")));
    }

    public record CreatedNotifications(List<UUID> notificationIds, String status) {}
}
