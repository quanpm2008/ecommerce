package com.example.product_service.product.dto.response;

import com.example.product_service.product.dto.model.MediaItem;
import com.example.product_service.product.enums.ProductStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminProductResponse {
    private UUID productId;
    private String productName;
    private Long priceVnd;
    private String coverImageUrl;
    private Double ratingAverage;
    private Long ratingCount;
    private String stockStatus;
    private UUID sellerId;
    private String sellerName;
    private ProductStatus status;
    private String categoryName;
    private String removalReason;
    private OffsetDateTime removedAt;
    private OffsetDateTime restoreDeadline;
    private Integer restoreDaysLeft;
    private List<MediaItem> media;
    private String description;
}
