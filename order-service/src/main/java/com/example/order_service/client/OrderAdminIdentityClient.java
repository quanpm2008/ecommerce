package com.example.order_service.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "identity-service", contextId = "orderAdminIdentity",
		url = "${order.identity.base-url:http://localhost:8083}")
public interface OrderAdminIdentityClient {
	@GetMapping("/api/v1/identity/admin/customers/{id}")
	Envelope customer(@PathVariable("id") UUID id, @RequestHeader("Authorization") String bearer);

	record Envelope(Customer data) {}
	record Customer(User user) {}
	record User(UUID userId, String fullName, String email) {}
}
