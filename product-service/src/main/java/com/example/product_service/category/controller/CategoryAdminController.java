package com.example.product_service.category.controller;

import com.example.product_service.category.dto.CategoryAdminResponse;
import com.example.product_service.category.dto.CreateCategoryRequest;
import com.example.product_service.category.dto.UpdateCategoryRequest;
import com.example.product_service.category.service.CategoryService;
import com.example.product_service.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "6. Categories - Admin", description = "Quản trị danh mục (Admin)")
@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class CategoryAdminController {

    private final CategoryService categoryService;

    @Operation(summary = "Xem tất cả danh mục bao gồm cả danh mục ẩn (kèm số lượng sản phẩm)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryAdminResponse>>> getAllCategories() {
        ApiResponse<List<CategoryAdminResponse>> response = categoryService.getAllCategories();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Thêm mới danh mục")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryAdminResponse>> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        ApiResponse<CategoryAdminResponse> response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(summary = "Cập nhật tên hoặc ẩn/hiện danh mục")
    @PatchMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryAdminResponse>> updateCategory(
            @PathVariable String categoryId,
            @Valid @RequestBody UpdateCategoryRequest request) {
        ApiResponse<CategoryAdminResponse> response = categoryService.updateCategory(categoryId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Xóa danh mục (chỉ xóa được khi danh mục chưa gắn với sản phẩm nào)")
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> deleteCategory(@PathVariable String categoryId) {
        categoryService.deleteCategory(categoryId);
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
