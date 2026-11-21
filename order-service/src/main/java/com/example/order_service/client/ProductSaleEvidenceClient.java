package com.example.order_service.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "product-service", contextId = "productSaleEvidence", url = "${order.product.base-url:http://localhost:8087}")
public interface ProductSaleEvidenceClient {
	@PostMapping("/internal/v1/products/sale-evidence")
	Envelope read(@RequestHeader("X-Service-Token") String credential, @RequestBody Request request);

	record Request(String completedAt, List<UUID> productIds) {}
	record Envelope(Data data) {}
	record Data(String completedAt, List<Item> items) {}
	record Item(UUID productId, @JsonProperty(required = true) UUID sellerId, String decision) {}
}
