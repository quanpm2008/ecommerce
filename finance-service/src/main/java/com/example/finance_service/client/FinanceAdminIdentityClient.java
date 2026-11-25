package com.example.finance_service.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "identity-service", contextId = "financeAdminIdentity", url = "${finance.identity.base-url:http://localhost:8083}")
public interface FinanceAdminIdentityClient {
    @GetMapping("/api/v1/identity/admin/sellers/{sellerId}")
    Envelope seller(@PathVariable("sellerId") UUID sellerId, @RequestHeader("Authorization") String bearer);
    record Envelope(Data data) {}
    record Data(User user, Profile sellerProfile) {}
    record User(UUID userId) {}
    record Profile(String sellerName) {}
}
