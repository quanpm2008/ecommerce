package com.example.product_service.client.identity;

import com.example.product_service.client.identity.dto.SellerStatusDto;
import com.example.product_service.common.response.ApiResponse;
import com.example.product_service.media.common.MediaPurpose;
import com.example.product_service.product.dto.SellerPublicDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import feign.FeignException;

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

    public UUID requireSeller() {
        IdentityClient.SellerAuthorization auth = fetchAuthorizationContext();
        if (!"SELLER".equals(auth.accountType()) || !"ACTIVE".equals(auth.accountStatus())
                || auth.permissions() == null || !auth.permissions().contains("product:manage:own")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return auth.userId();
    }

    public UUID requirePermission(String permission) {
        IdentityClient.SellerAuthorization auth = fetchAuthorizationContext();
        if (!"ACTIVE".equals(auth.accountStatus())
                || auth.permissions() == null || !auth.permissions().contains(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return auth.userId();
    }

    public UUID requireCustomerForReview() {
        return requirePermission("review:write:own");
    }

    public void requireMediaUploadPermission(List<MediaPurpose> purposes) {
        if (purposes == null || purposes.isEmpty()) {
            return;
        }
        boolean needsSeller = purposes.stream().anyMatch(p -> p == MediaPurpose.PRODUCT_MEDIA);
        boolean needsReview = purposes.stream().anyMatch(p -> p == MediaPurpose.REVIEW_MEDIA);
        if (needsSeller) {
            requireSeller();
        }
        if (needsReview) {
            requireCustomerForReview();
        }
    }

    private IdentityClient.SellerAuthorization fetchAuthorizationContext() {
        IdentityClient.SellerAuthorization auth;
        try {
            var reply = identityClient.authorizationContext();
            auth = reply == null ? null : reply.getData();
        } catch (FeignException error) {
            HttpStatus status = error.status() == 401 ? HttpStatus.UNAUTHORIZED
                    : HttpStatus.SERVICE_UNAVAILABLE;
            throw new ResponseStatusException(status);
        }
        if (auth == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!auth.sessionActive() || auth.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return auth;
    }

    public boolean requireCurrentSellability(UUID sellerId) {
        return Boolean.TRUE.equals(requireSellableMap(List.of(sellerId)).get(sellerId));
    }

    public Map<UUID, Boolean> requireSellableMap(List<UUID> sellerIds) {
        try {
            var response = identityClient.getSellerStatuses(sellerIds);
            if (response != null && response.getData() != null) {
                return toSellableMap(response.getData());
            }
        } catch (FeignException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", unavailable);
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
    }

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
        return toSellableMap(getSellerStatuses(sellerIds));
    }

    private Map<UUID, Boolean> toSellableMap(List<SellerStatusDto> statuses) {
        return statuses.stream()
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
