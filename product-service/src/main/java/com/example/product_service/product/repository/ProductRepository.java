package com.example.product_service.product.repository;

import com.example.product_service.product.entity.Product;
import com.example.product_service.product.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.domain.Pageable;
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

    @Modifying
    @Query(value = "update products set status = 'SELLING', updated_at = statement_timestamp() where product_id = :productId and seller_id = :sellerId and status = 'HIDDEN'",
            nativeQuery = true)
    int publishProvisioned(UUID productId, UUID sellerId);
}
