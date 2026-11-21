package com.example.cart_service.client;

import com.example.cart_service.common.web.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "product-service", contextId = "cartProductClient", url = "${cart.clients.product-url:}",
		configuration = InternalFeignConfig.class)
interface ProductClient {

	/** Body: a JSON array of product ids, as product-service implements it. */
	@PostMapping("/internal/v1/products/batch")
	ApiResponse<List<ProductSnapshot>> batch(@RequestBody List<UUID> productIds);

}
