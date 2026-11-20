package com.example.product_service.product.dto.response;

import com.example.product_service.product.dto.CategorySummaryDto;
import com.example.product_service.product.dto.SellerPublicDto;
import com.example.product_service.product.dto.model.MediaItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailResponse {
    private UUID productId;

    private String productName;

    private Long priceVnd;

    private String coverImageUrl;

    private Double ratingAverage;

    private Long ratingCount;

    private String stockStatus;

    private String description;

    private CategorySummaryDto category;

    private List<MediaItem> media;

    private Integer availableQuantity;

    private Integer maxPerOrder;

    private SellerPublicDto seller;
}
