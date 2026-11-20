package com.example.order_service.inventory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class InventoryProvisionRepository {

	private final JdbcTemplate jdbc;

	public InventoryProvisionRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	boolean create(UUID productId, UUID sellerId, int onHandQuantity) {
		return jdbc.update("""
				insert into orders.inventory (product_id, seller_id, on_hand_quantity)
				values (?, ?, ?) on conflict (product_id) do nothing
				""", productId, sellerId, onHandQuantity) == 1;
	}

	UUID sellerOf(UUID productId) {
		return jdbc.queryForObject("select seller_id from orders.inventory where product_id = ?", UUID.class, productId);
	}
}
