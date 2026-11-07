package com.example.product_service.product.dto.request;

import com.example.product_service.product.dto.model.MediaItem;
import com.example.product_service.product.enums.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    @NotBlank
    @Size(max = 255)
    private String productName;

    @NotNull
    private UUID categoryId;

    @NotNull
    @Min(value = 0)
    private Long priceVnd;

    @NotNull
    @Min(value = 0)
    private Integer initialStock;

    @Size(max = 5000)
    private String description;

    @NotEmpty
    private List<@Valid MediaItem> media;

    private ProductStatus status;
}
