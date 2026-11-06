package com.example.product_service.category.controller;

import com.example.product_service.category.dto.CategoryResponse;
import com.example.product_service.category.service.CategoryService;
import com.example.product_service.common.response.ApiPageResponse;
import com.example.product_service.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryPublicController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getVisibleCategories() {
        ApiResponse<List<CategoryResponse>> response = categoryService.getVisibleCategories();
        return ResponseEntity.ok(response);
    }

}
