package com.example.product_service.category.repository;

import com.example.product_service.category.entity.Category;
import com.example.product_service.category.enums.CategoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    // Lấy danh sách danh mục theo trạng thái và sắp xếp tên tăng dần
    List<Category> findAllByStatusOrderByCategoryNameAsc(CategoryStatus status);

    List<Category> findAllByOrderByCategoryNameAsc();

    // Kiểm tra xem danh mục có tồn tại theo tên (không phân biệt chữ hoa chữ thường)
    boolean existsByCategoryNameIgnoreCase(String categoryName);

    boolean existsByCategoryNameIgnoreCaseAndCategoryIdNot(String categoryName, UUID id);

}
