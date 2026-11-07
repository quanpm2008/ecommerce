package com.example.product_service.product.repository;

import com.example.product_service.product.entity.Product;
import com.example.product_service.product.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.awt.print.Pageable;
import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>,
        JpaSpecificationExecutor<Product> {

    // Kiểm tra danh mục có sản phẩm nào không (chặn xóa category)
    boolean existsByCategoryId(UUID categoryId);

    long countByCategoryId(UUID categoryId);

    List<Product> findByCategoryIdAndStatusAndProductIdNot(
            UUID categoryId,
            ProductStatus status,
            UUID productId,
            Pageable pageable
    );

    Page<Product> findBySellerId(UUID sellerId, Pageable pageable);

    List<Product> findByProductIdIn(List<UUID> productIds);
}
