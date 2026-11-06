package com.example.product_service.category.service;

import com.example.product_service.category.dto.CategoryAdminResponse;
import com.example.product_service.category.dto.CategoryResponse;
import com.example.product_service.category.dto.CreateCategoryRequest;
import com.example.product_service.category.dto.UpdateCategoryRequest;
import com.example.product_service.category.utils.dto.ApiPageResponse;
import com.example.product_service.category.utils.dto.ApiResponse;

import java.util.List;

public interface CategoryService {

    ApiResponse<List<CategoryResponse>> getVisibleCategories();

    ApiResponse<List<CategoryAdminResponse>> getAllCategories();

    ApiResponse<CategoryAdminResponse> createCategory(CreateCategoryRequest request);

    ApiResponse<CategoryAdminResponse> updateCategory(String categoryId, UpdateCategoryRequest request);

    ApiResponse<Void> deleteCategory(String categoryId);

}
