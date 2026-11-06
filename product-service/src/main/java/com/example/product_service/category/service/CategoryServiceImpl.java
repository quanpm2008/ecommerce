package com.example.product_service.category.service;

import com.example.product_service.category.dto.CategoryAdminResponse;
import com.example.product_service.category.dto.CategoryResponse;
import com.example.product_service.category.dto.CreateCategoryRequest;
import com.example.product_service.category.dto.UpdateCategoryRequest;
import com.example.product_service.category.entity.Category;
import com.example.product_service.category.enums.CategoryStatus;
import com.example.product_service.category.exception.CategoryInUseException;
import com.example.product_service.category.exception.CategoryNameDuplicateException;
import com.example.product_service.category.repository.CategoryRepository;
import com.example.product_service.category.utils.dto.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;


@Service
public class CategoryServiceImpl implements CategoryService{

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public ApiResponse<List<CategoryResponse>> getVisibleCategories() {

        List<Category> categories = categoryRepository.findAllByStatusOrderByCategoryNameAsc(CategoryStatus.VISIBLE);

        List<CategoryResponse> categoryResponses = categories.stream()
                .map(category -> CategoryResponse.builder()
                        .categoryId(category.getCategoryId())
                        .categoryName(category.getCategoryName())
                        .status(category.getStatus())
                        .build())
                .toList();

        return ApiResponse.of(categoryResponses);
    }

    //Sua sau khi hoan thien produc entity
    public ApiResponse<List<CategoryAdminResponse>> getAllCategories() {

        List<Category> categories = categoryRepository.findAllByOrderByCategoryNameAsc();

        List<CategoryAdminResponse> categoryAdminResponses = categories.stream()
                .map(category -> CategoryAdminResponse.builder()
                        .categoryId(category.getCategoryId())
                        .categoryName(category.getCategoryName())
                        .status(category.getStatus())
                        .productCount(0L)
                        .build())
                .toList();
        return ApiResponse.of(categoryAdminResponses);
    }

    public ApiResponse<CategoryAdminResponse> createCategory(CreateCategoryRequest request) {

        String categoryName = request.getCategoryName().trim();

        if (categoryRepository.existsByCategoryNameIgnoreCase(categoryName)) {
            throw new CategoryNameDuplicateException("Tên danh mục đã tồn tại: " + categoryName);
        }

        Category category = Category.builder()
                .categoryName(categoryName)
                .status(request.getStatus() != null ? request.getStatus() : CategoryStatus.VISIBLE)
                .build();

        categoryRepository.save(category);

        CategoryAdminResponse categoryAdminResponse = CategoryAdminResponse.builder()
                .categoryId(category.getCategoryId())
                .categoryName(category.getCategoryName())
                .status(category.getStatus())
                .productCount(0L)
                .build();

        return ApiResponse.of(categoryAdminResponse);
    }

    public ApiResponse<CategoryAdminResponse> updateCategory(String categoryId, UpdateCategoryRequest request) {

        UUID id = UUID.fromString(categoryId);

        Category category = categoryRepository.findById(id).orElseThrow(
                () -> new EntityNotFoundException("Không tìm thấy danh mục: " + categoryId)
        );

        if (request.getCategoryName() == null && request.getStatus() == null) {
            throw new IllegalArgumentException("Phải cung cấp ít nhất một trường cần cập nhật");
        }

        if (request.getCategoryName() != null) {
            String categoryName = request.getCategoryName().trim();

            if (categoryRepository.existsByCategoryNameIgnoreCase(categoryName)) {
                throw new CategoryNameDuplicateException("Tên danh mục đã tồn tại: " + categoryName);
            }

            category.setCategoryName(categoryName);
        }

        if (request.getStatus() != null) {
            category.setStatus(request.getStatus());
        }

        Category updated = categoryRepository.save(category);

        CategoryAdminResponse categoryAdminResponse = CategoryAdminResponse.builder()
                .categoryName(updated.getCategoryName())
                .categoryId(updated.getCategoryId())
                .status(updated.getStatus())
                .productCount(0L)
                .build();

        return ApiResponse.of(categoryAdminResponse);
    }

    public ApiResponse<Void> deleteCategory(String categoryId) {

        UUID id = UUID.fromString(categoryId);

        Category category = categoryRepository.findById(id).orElseThrow(
                () -> new EntityNotFoundException("Không tìm thấy danh mục: " + categoryId)
        );

        long productCount = 0L;

        if (productCount > 0) {
            throw new CategoryInUseException("Không thể xóa danh mục đang có sản phẩm");
        }

        categoryRepository.deleteById(id);

        return ApiResponse.of(null);
    }
}
