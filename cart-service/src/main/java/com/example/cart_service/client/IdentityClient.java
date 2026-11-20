package com.example.cart_service.client;

import com.example.cart_service.common.web.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/** Identity endpoints Cart uses (API §7.1 internal, plus the public seller page for the shop name). */
@FeignClient(name = "identity-service", contextId = "cartIdentityClient", url = "${cart.clients.identity-url:}",
		configuration = InternalFeignConfig.class)
interface IdentityClient {

	@PostMapping("/internal/v1/identity/auth/authorization-context")
	ApiResponse<AuthorizationContext> authorizationContext(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer);

	@GetMapping("/internal/v1/identity/sellers/status")
	ApiResponse<List<SellerStatus>> sellerStatuses(@RequestParam("ids") List<UUID> sellerIds);

	@GetMapping("/api/v1/identity/sellers/{sellerId}")
	ApiResponse<SellerPublic> sellerPublic(@PathVariable("sellerId") UUID sellerId);

	/** API §6.1 SellerPublic; Cart only reads the shop name. */
	record SellerPublic(UUID sellerId, String sellerName) {
	}

}
