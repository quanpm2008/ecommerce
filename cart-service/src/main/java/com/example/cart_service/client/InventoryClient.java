package com.example.cart_service.client;

import com.example.cart_service.common.web.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/** Order owns inventory (G-04); Cart only reads availability for display (API §7.1). */
@FeignClient(name = "order-service", contextId = "cartInventoryClient", url = "${cart.clients.order-url:}",
		configuration = InternalFeignConfig.class)
interface InventoryClient {

	@GetMapping("/internal/v1/inventory/availability")
	ApiResponse<List<Availability>> availability(@RequestParam("productIds") List<UUID> productIds);

	record Availability(UUID productId, Integer availableQuantity) {
	}

}
