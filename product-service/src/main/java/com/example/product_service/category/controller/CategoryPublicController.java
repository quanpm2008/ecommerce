package com.example.product_service.category.controller;

import com.example.product_service.category.dto.CategoryResponse;
import com.example.product_service.category.service.CategoryService;
import com.example.product_service.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "1. Categories - Public", description = "Xem danh mục sản phẩm công khai")
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryPublicController {

    private final CategoryService categoryService;

    @Operation(summary = "Lấy danh sách các danh mục đang hiển thị (VISIBLE)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getVisibleCategories() {
        ApiResponse<List<CategoryResponse>> response = categoryService.getVisibleCategories();
        return ResponseEntity.ok(response);
    }

}
