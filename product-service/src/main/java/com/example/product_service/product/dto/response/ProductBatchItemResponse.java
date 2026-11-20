package com.example.product_service.product.dto.response;

import com.example.product_service.product.dto.model.MediaItem;
import com.example.product_service.product.enums.ProductStatus;
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
public class ProductBatchItemResponse {
    private UUID productId;
    private UUID sellerId;
    private Long priceVnd;
    private String productName;
    private String coverImageUrl;
    private List<MediaItem> media;
    private ProductStatus status;
    private Boolean sellable;
}
