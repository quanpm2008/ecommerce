package com.example.order_service.service;

import com.example.order_service.repository.InventoryStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InventoryProvisionService {

	private final InventoryStockRepository repository;

	public InventoryProvisionService(InventoryStockRepository repository) {
		this.repository = repository;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Result provision(UUID productId, UUID sellerId, int onHandQuantity) {
		if (repository.create(productId, sellerId, onHandQuantity) == 1) {
			return Result.CREATED;
		}
		return repository.findBySellerIdAndProductId(sellerId, productId).isPresent() ? Result.EXISTS : Result.OWNER_CONFLICT;
	}

	public enum Result {
		CREATED, EXISTS, OWNER_CONFLICT
	}
}
