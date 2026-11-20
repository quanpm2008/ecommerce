package com.example.product_service.client.identity;

import com.example.product_service.client.identity.dto.SellerStatusDto;
import com.example.product_service.common.response.ApiResponse;
import com.example.product_service.product.dto.SellerPublicDto;
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
public class IdentityClientAdapter {

    private final IdentityClient identityClient;

    public List<SellerStatusDto> getSellerStatuses(List<UUID> sellerIds) {
        if (sellerIds == null || sellerIds.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            ApiResponse<List<SellerStatusDto>> response = identityClient.getSellerStatuses(sellerIds);
            if (response != null && response.getData() != null) {
                return response.getData();
            }
        } catch (Exception e) {
            log.error("Failed to fetch seller statuses from Identity Service: {}", e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    public Map<UUID, Boolean> getSellableMap(List<UUID> sellerIds) {
        return getSellerStatuses(sellerIds).stream()
                .collect(Collectors.toMap(
                        SellerStatusDto::getSellerId,
                        dto -> Boolean.TRUE.equals(dto.getSellable()),
                        (existing, replacement) -> existing
                ));
    }

    public boolean isSellerSellable(UUID sellerId) {
        if (sellerId == null) {
            return false;
        }
        Map<UUID, Boolean> map = getSellableMap(List.of(sellerId));
        return Boolean.TRUE.equals(map.get(sellerId));
    }

    public SellerPublicDto getSellerPublic(UUID sellerId) {
        try {
            ApiResponse<SellerPublicDto> response = identityClient.getSellerPublic(sellerId);
            if (response != null && response.getData() != null) {
                return response.getData();
            }
        } catch (Exception e) {
            log.warn("Không thể lấy SellerPublic từ IdentityClient: {}", e.getMessage());
        }
        return SellerPublicDto.builder()
                .sellerId(sellerId)
                .sellerName("Gian hàng")
                .description(null)
                .logoUrl(null)
                .build();
    }
}
