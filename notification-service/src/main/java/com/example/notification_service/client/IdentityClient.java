package com.example.notification_service.client;

import com.example.notification_service.common.web.ApiResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "identity-service", contextId = "notificationIdentity",
        url = "${notification.clients.identity-url:}", configuration = IdentityFeignConfiguration.class)
public interface IdentityClient {
    @PostMapping("/internal/v1/identity/auth/authorization-context")
    ApiResponse<IdentityGateway.AuthorizationContext> authorizationContext(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Service-Token") String serviceToken);

    @GetMapping("/internal/v1/identity/users/{userId}")
    ApiResponse<IdentityGateway.UserReference> recipient(@PathVariable("userId") UUID userId,
            @RequestHeader("X-Service-Token") String serviceToken);
}
