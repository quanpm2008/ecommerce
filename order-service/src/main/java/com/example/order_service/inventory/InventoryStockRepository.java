package com.example.order_service.inventory;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class InventoryStockRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public InventoryStockRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Availability> availability(List<UUID> productIds) {
		return jdbc.query("""
				select product_id, on_hand_quantity - reserved_quantity as available_quantity
				from orders.inventory where product_id in (:ids) order by product_id
				""", Map.of("ids", productIds), (row, index) -> new Availability(
				row.getObject("product_id", UUID.class), row.getInt("available_quantity")));
	}

	public record Availability(UUID productId, int availableQuantity) {
	}
}
