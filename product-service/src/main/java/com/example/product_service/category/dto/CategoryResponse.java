package com.example.product_service.category.dto;

import com.example.product_service.category.enums.CategoryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    private UUID categoryId;

    private String categoryName;

    private CategoryStatus status;
}
