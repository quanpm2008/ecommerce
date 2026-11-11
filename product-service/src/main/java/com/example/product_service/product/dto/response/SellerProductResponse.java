package com.example.product_service.product.dto.response;

import com.example.product_service.product.dto.CategorySummaryDto;
import com.example.product_service.product.dto.model.MediaItem;
import com.example.product_service.product.enums.ProductStatus;
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
public class SellerProductResponse {

    private UUID productId;

    private String productName;

    private CategorySummaryDto category;

    private Long priceVnd;

    private ProductStatus status;

    private String coverImageUrl;

    private List<MediaItem> media;

    private String description;

    private Integer onHandQuantity;

    private Integer reservedQuantity;

    private Integer availableQuantity;

    private Long inventoryVersion;

    private String removalReason;

    private OffsetDateTime removedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
