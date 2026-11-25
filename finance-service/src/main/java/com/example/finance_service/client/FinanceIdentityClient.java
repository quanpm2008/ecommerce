package com.example.finance_service.client;

import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "identity-service", contextId = "financeIdentity", url = "${finance.identity.base-url:http://localhost:8083}")
public interface FinanceIdentityClient {
    @PostMapping("/internal/v1/identity/auth/authorization-context")
    Envelope context(@RequestHeader("X-Service-Token") String serviceCredential,
            @RequestHeader("Authorization") String bearer);

    record Envelope(Context data) {}
    record Context(UUID userId, String accountType, String accountStatus, String userStatus, String accessMode,
                   Long authzVersion, Boolean sessionActive, List<String> roles, List<String> permissions,
                   Seller sellerProfile) {}
    record Seller(String approvalStatus, String status) {}
}
