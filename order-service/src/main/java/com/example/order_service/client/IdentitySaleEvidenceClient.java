package com.example.order_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "identity-service", contextId = "identitySaleEvidence", url = "${order.identity.base-url:http://localhost:8083}")
public interface IdentitySaleEvidenceClient {
	@PostMapping("/internal/v1/identity/sellers/sale-evidence")
	Envelope read(@RequestHeader("X-Service-Token") String credential, @RequestBody Request request);

	record Request(String completedAt, List<UUID> sellerIds) {}
	record Envelope(Data data) {}
	record Data(String completedAt, List<Item> items) {}
	record Item(UUID sellerId, String decision) {}
}
