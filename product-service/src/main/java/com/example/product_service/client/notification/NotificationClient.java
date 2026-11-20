package com.example.product_service.client.notification;

import com.example.product_service.client.config.FeignConfig;
import com.example.product_service.client.notification.dto.CreateNotificationInternalRequest;
import com.example.product_service.client.notification.dto.NotificationCreationResponse;
import com.example.product_service.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", configuration = FeignConfig.class)
public interface NotificationClient {

    @PostMapping("/internal/v1/notifications")
    ApiResponse<NotificationCreationResponse> sendNotification(@RequestBody CreateNotificationInternalRequest request);
}
