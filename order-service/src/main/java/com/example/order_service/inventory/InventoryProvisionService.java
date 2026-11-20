package com.example.order_service.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InventoryProvisionService {

	private final InventoryProvisionRepository repository;

	public InventoryProvisionService(InventoryProvisionRepository repository) {
		this.repository = repository;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Result provision(UUID productId, UUID sellerId, int onHandQuantity) {
		if (repository.create(productId, sellerId, onHandQuantity)) {
			return Result.CREATED;
		}
		return sellerId.equals(repository.sellerOf(productId)) ? Result.EXISTS : Result.OWNER_CONFLICT;
	}

	public enum Result {
		CREATED, EXISTS, OWNER_CONFLICT
	}
}
