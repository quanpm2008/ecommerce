package com.example.product_service.client.order;

import com.example.product_service.client.config.FeignConfig;
import com.example.product_service.client.order.dto.InitInventoryRequest;
import com.example.product_service.client.order.dto.InventoryAvailabilityDto;
import com.example.product_service.client.order.dto.InventoryDetailDto;
import com.example.product_service.client.order.dto.ReviewEligibilityDto;
import com.example.product_service.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "order-service", configuration = FeignConfig.class)
public interface OrderInventoryClient {

    @PostMapping("/internal/v1/inventory")
    ApiResponse<Void> initInventory(@RequestBody InitInventoryRequest request);

    @GetMapping("/internal/v1/inventory/availability")
    ApiResponse<List<InventoryAvailabilityDto>> getAvailability(@RequestParam("productIds") List<UUID> productIds);

    @GetMapping("/internal/v1/inventory/details")
    ApiResponse<List<InventoryDetailDto>> getDetails(@RequestParam("productIds") List<UUID> productIds);

    @GetMapping("/internal/v1/orders/order-items/{orderItemId}/review-eligibility")
    ApiResponse<ReviewEligibilityDto> checkReviewEligibility(
            @PathVariable("orderItemId") UUID orderItemId,
            @RequestParam("customerId") UUID customerId);

}
