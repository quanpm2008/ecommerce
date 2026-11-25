package com.example.order_service.repository;

import com.example.order_service.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
	Page<StockMovement> findByProductIdOrderByCreatedAtDescStockMovementId(UUID productId, Pageable page);
}
