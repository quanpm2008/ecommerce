package com.example.product_service.client.identity;

import com.example.product_service.client.config.FeignConfig;
import com.example.product_service.client.identity.dto.SellerStatusDto;
import com.example.product_service.common.response.ApiResponse;
import com.example.product_service.product.dto.SellerPublicDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "identity-service", url = "${product.identity.base-url:}", configuration = FeignConfig.class)
public interface IdentityClient {

    @GetMapping("/internal/v1/identity/sellers/status")
    ApiResponse<List<SellerStatusDto>> getSellerStatuses(@RequestParam("ids") List<UUID> sellerIds);

    @GetMapping("/api/v1/identity/sellers/{sellerId}")
    ApiResponse<SellerPublicDto> getSellerPublic(@PathVariable("sellerId") UUID sellerId);

    @PostMapping("/internal/v1/identity/auth/authorization-context")
    ApiResponse<SellerAuthorization> authorizationContext();

    record SellerAuthorization(UUID userId, String accountType, String accountStatus,
                               boolean sessionActive, List<String> permissions) {}
}
