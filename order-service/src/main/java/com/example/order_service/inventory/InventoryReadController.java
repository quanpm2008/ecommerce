package com.example.order_service.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/inventory")
public class InventoryReadController {

	private final InventoryStockRepository inventory;

	public InventoryReadController(InventoryStockRepository inventory) {
		this.inventory = inventory;
	}

	@GetMapping("/availability")
	Data<List<InventoryStockRepository.Availability>> availability(@RequestParam List<UUID> productIds) {
		if (productIds.isEmpty() || productIds.contains(null)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		}
		return new Data<>(inventory.availability(productIds));
	}

	public record Data<T>(T data) {
	}
}
