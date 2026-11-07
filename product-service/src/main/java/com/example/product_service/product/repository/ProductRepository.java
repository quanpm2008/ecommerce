package com.example.product_service.product.repository;

import com.example.product_service.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>,
        JpaSpecificationExecutor<Product> {

    // Kiểm tra danh mục có sản phẩm nào không (chặn xóa category)
    boolean existsByCategoryId(UUID categoryId);

    long countByCategoryId(UUID categoryId);
}
