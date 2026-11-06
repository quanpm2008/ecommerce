package com.example.product_service.category.controller;

import com.example.product_service.category.dto.CategoryAdminResponse;
import com.example.product_service.category.dto.CreateCategoryRequest;
import com.example.product_service.category.dto.UpdateCategoryRequest;
import com.example.product_service.category.service.CategoryService;
import com.example.product_service.category.utils.dto.ApiPageResponse;
import com.example.product_service.category.utils.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class CategoryAdminController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryAdminResponse>>> getAllCategories() {
        ApiResponse<List<CategoryAdminResponse>> response = categoryService.getAllCategories();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CategoryAdminResponse>> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        ApiResponse<CategoryAdminResponse> response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @PatchMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryAdminResponse>> updateCategory(
            @PathVariable String categoryId,
            @Valid @RequestBody UpdateCategoryRequest request) {
        ApiResponse<CategoryAdminResponse> response = categoryService.updateCategory(cgitategoryId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> deleteCategory(@PathVariable String categoryId) {
        categoryService.deleteCategory(categoryId);
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
