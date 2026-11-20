package com.example.product_service.category.dto;

import com.example.product_service.category.enums.CategoryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCategoryRequest {
    @NotBlank
    @Size(min= 1, max = 150)
    private String categoryName;

    private CategoryStatus status;
}
