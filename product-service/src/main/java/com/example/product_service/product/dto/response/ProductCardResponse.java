package com.example.product_service.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCardResponse {
    private UUID productId;
    private String productName;
    private Long priceVnd;
    private String coverImageUrl;
    private Double ratingAverage;
    private Long ratingCount;
    private String stockStatus;
    private UUID sellerId;
    private String sellerName;
}
