package com.example.order_service.controller;

import com.example.order_service.exception.OrderApiExceptionHandler;
import com.example.order_service.service.InventoryProvisionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

@RestController
public class InventoryProvisionController {
	private final InventoryProvisionService inventory;
	private final OrderApiExceptionHandler errors;

	public InventoryProvisionController(InventoryProvisionService inventory, OrderApiExceptionHandler errors) {
		this.inventory = inventory;
		this.errors = errors;
	}

	@PostMapping("/internal/v1/inventory")
	ResponseEntity<ProvisionResponse> provision(@RequestBody ProvisionRequest request) {
		if (request.productId() == null || request.sellerId() == null || request.onHandQuantity() == null
				|| request.onHandQuantity() < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		}
		return switch (inventory.provision(request.productId(), request.sellerId(), request.onHandQuantity())) {
			case CREATED -> ResponseEntity.status(HttpStatus.CREATED).body(new ProvisionResponse(null));
			case EXISTS -> ResponseEntity.ok(new ProvisionResponse(null));
			case OWNER_CONFLICT -> throw new OwnerConflict();
		};
	}

	@ExceptionHandler(OwnerConflict.class)
	void ownerConflict(HttpServletRequest request, HttpServletResponse response) throws IOException {
		errors.write(request, response, 409, "INVENTORY_PROVISION_CONFLICT", "Sản phẩm đã có tồn kho của Seller khác");
	}

	record ProvisionRequest(UUID productId, UUID sellerId, Integer onHandQuantity) {
	}

	record ProvisionResponse(Void data) {
	}

	private static final class OwnerConflict extends RuntimeException {
	}
}
