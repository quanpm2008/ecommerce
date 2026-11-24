package com.example.payment_service.client;

import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "identity-service", contextId = "paymentIdentity",
        url = "${payment.identity.base-url:http://localhost:8083}")
public interface PaymentIdentityClient {
    @PostMapping("/internal/v1/identity/auth/authorization-context")
    Envelope context(@RequestHeader("X-Service-Token") String credential, @RequestHeader("Authorization") String bearer);

    record Envelope(Context data) {}
    record Context(UUID userId, String accountType, String accountStatus, String userStatus, String accessMode,
                   Long authzVersion, Boolean sessionActive, List<String> roles, List<String> permissions, Profile customerProfile) {}
    record Profile(String status) {}
}
