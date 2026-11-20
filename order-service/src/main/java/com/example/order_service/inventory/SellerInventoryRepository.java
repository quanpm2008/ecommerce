package com.example.order_service.inventory;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class SellerInventoryRepository {
	private static final String STOCK = """
			select product_id, on_hand_quantity, reserved_quantity, row_version, updated_at
			from orders.inventory where seller_id = :seller
			""";
	private static final RowMapper<Stock> STOCK_ROW = (row, index) -> new Stock(
			row.getObject("product_id", UUID.class), row.getInt("on_hand_quantity"), row.getInt("reserved_quantity"),
			row.getLong("row_version"), row.getObject("updated_at", OffsetDateTime.class));

	private final NamedParameterJdbcTemplate jdbc;

	SellerInventoryRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	List<Stock> list(UUID seller, List<UUID> ids, long offset, int size) {
		var parameters = new HashMap<String, Object>(Map.of("seller", seller, "size", size, "offset", offset));
		String query = STOCK;
		if (ids != null) {
			query += " and product_id in (:ids)";
			parameters.put("ids", ids);
		}
		return jdbc.query(query + " order by updated_at desc, product_id limit :size offset :offset", parameters, STOCK_ROW);
	}

	Optional<Stock> get(UUID seller, UUID product, boolean lock) {
		return jdbc.query(STOCK + " and product_id = :product" + (lock ? " for update" : ""),
				Map.of("seller", seller, "product", product), STOCK_ROW).stream().findFirst();
	}

	Stock update(UUID seller, UUID product, int quantity) {
		return jdbc.queryForObject("""
				update orders.inventory set on_hand_quantity = :quantity, row_version = row_version + 1,
				updated_at = statement_timestamp() where seller_id = :seller and product_id = :product
				returning product_id, on_hand_quantity, reserved_quantity, row_version, updated_at
				""", Map.of("seller", seller, "product", product, "quantity", quantity), STOCK_ROW);
	}

	void recordAdjustment(UUID seller, UUID product, int delta, long version, String note) {
		var args = new HashMap<String, Object>(Map.of("id", UUID.randomUUID(), "seller", seller,
				"product", product, "delta", delta, "key", "manual:" + product + ":" + version));
		args.put("note", note);
		jdbc.update("""
				insert into orders.stock_movement
				(stock_movement_id, product_id, movement_type, quantity_delta, actor_id, actor_type, business_event_key, note)
				values (:id, :product, 'MANUAL_ADJUST', :delta, :seller, 'USER', :key, :note)
				""", args);
	}

	List<Movement> movements(UUID product, long offset, int size) {
		return jdbc.query("""
				select stock_movement_id, movement_type, quantity_delta, order_item_id, actor_type, note, created_at
				from orders.stock_movement where product_id = :product
				order by created_at desc, stock_movement_id limit :size offset :offset
				""", Map.of("product", product, "size", size, "offset", offset), (row, index) -> new Movement(
				row.getObject("stock_movement_id", UUID.class), row.getString("movement_type"), row.getInt("quantity_delta"),
				row.getObject("order_item_id", UUID.class), null, row.getString("actor_type"), row.getString("note"),
				row.getObject("created_at", OffsetDateTime.class)));
	}

	long movementCount(UUID product) {
		return jdbc.queryForObject("select count(*) from orders.stock_movement where product_id = :product",
				Map.of("product", product), Long.class);
	}

	record Stock(UUID productId, int onHand, int reserved, long version, OffsetDateTime updatedAt) {
	}

	record Movement(UUID movementId, String movementType, int quantityDelta, UUID orderItemId, UUID orderId,
			String actorType, String note, OffsetDateTime createdAt) {
	}
}
