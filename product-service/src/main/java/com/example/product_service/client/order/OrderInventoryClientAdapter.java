package com.example.product_service.client.order;

import com.example.product_service.client.order.dto.InitInventoryRequest;
import com.example.product_service.client.order.dto.InventoryAvailabilityDto;
import com.example.product_service.client.order.dto.InventoryDetailDto;
import com.example.product_service.client.order.dto.ReviewEligibilityDto;
import com.example.product_service.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderInventoryClientAdapter {

    private final OrderInventoryClient orderInventoryClient;

    public void initInventory(UUID productId, UUID sellerId, Integer onHandQuantity) {
        InitInventoryRequest request = InitInventoryRequest.builder()
                .productId(productId)
                .sellerId(sellerId)
                .onHandQuantity(onHandQuantity != null ? onHandQuantity : 0)
                .build();
        try {
            orderInventoryClient.initInventory(request);
        } catch (Exception e) {
            log.error("Failed to initialize inventory for product {}: {}", productId, e.getMessage(), e);
            throw e;
        }
    }

    public Map<UUID, Integer> getAvailabilityMap(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            ApiResponse<List<InventoryAvailabilityDto>> response = orderInventoryClient.getAvailability(productIds);
            if (response != null && response.getData() != null) {
                return response.getData().stream()
                        .collect(Collectors.toMap(
                                InventoryAvailabilityDto::getProductId,
                                dto -> dto.getAvailableQuantity() != null ? dto.getAvailableQuantity() : 0,
                                (existing, replacement) -> existing
                        ));
            }
        } catch (Exception e) {
            log.error("Failed to fetch inventory availability from Order Service: {}", e.getMessage(), e);
        }
        return Collections.emptyMap();
    }

    public Map<UUID, InventoryDetailDto> getDetailMap(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            ApiResponse<List<InventoryDetailDto>> response = orderInventoryClient.getDetails(productIds);
            if (response != null && response.getData() != null) {
                return response.getData().stream()
                        .collect(Collectors.toMap(
                                InventoryDetailDto::getProductId,
                                dto -> dto,
                                (existing, replacement) -> existing
                        ));
            }
        } catch (Exception e) {
            log.error("Failed to fetch inventory details from Order Service: {}", e.getMessage(), e);
        }
        return Collections.emptyMap();
    }

    public ReviewEligibilityDto checkReviewEligibility(UUID orderItemId, UUID customerId) {
        try {
            ApiResponse<ReviewEligibilityDto> response = orderInventoryClient.checkReviewEligibility(orderItemId, customerId);
            if (response != null && response.getData() != null) {
                return response.getData();
            }
        } catch (Exception e) {
            log.error("Failed to check review eligibility: {}", e.getMessage(), e);
        }
        return ReviewEligibilityDto.builder()
                .eligible(false)
                .build();
    }
}
