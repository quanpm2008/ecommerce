package com.example.order_service.repository;

import com.example.order_service.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryStockRepository extends JpaRepository<Inventory, UUID> {
	List<Inventory> findByProductIdInOrderByProductId(Collection<UUID> productIds);

	List<Inventory> findBySellerIdOrderByUpdatedAtDescProductId(UUID sellerId, Pageable page);

	List<Inventory> findBySellerIdAndProductIdInOrderByUpdatedAtDescProductId(UUID sellerId,
			Collection<UUID> productIds, Pageable page);

	Optional<Inventory> findBySellerIdAndProductId(UUID sellerId, UUID productId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Inventory i where i.sellerId = :sellerId and i.productId = :productId")
	Optional<Inventory> lockOwned(UUID sellerId, UUID productId);

	@Modifying
	@Query(value = """
			insert into orders.inventory (product_id, seller_id, on_hand_quantity)
			values (:productId, :sellerId, :quantity) on conflict (product_id) do nothing
			""", nativeQuery = true)
	int create(UUID productId, UUID sellerId, int quantity);

	@Modifying
	@Query(value = """
			update orders.inventory set reserved_quantity = reserved_quantity + :quantity,
				row_version = row_version + 1, updated_at = statement_timestamp()
			where product_id = :productId and seller_id = :sellerId
				and on_hand_quantity - reserved_quantity >= :quantity
			""", nativeQuery = true)
	int reserve(UUID productId, UUID sellerId, int quantity);

	@Modifying
	@Query(value = """
			update orders.inventory set reserved_quantity = reserved_quantity - :quantity,
				row_version = row_version + 1, updated_at = statement_timestamp()
			where product_id = :productId and reserved_quantity >= :quantity
			""", nativeQuery = true)
	int release(UUID productId, int quantity);

	@Modifying
	@Query(value = """
			update orders.inventory set on_hand_quantity = on_hand_quantity - :quantity,
				reserved_quantity = reserved_quantity - :quantity, row_version = row_version + 1,
				updated_at = statement_timestamp()
			where product_id = :productId and reserved_quantity >= :quantity and on_hand_quantity >= :quantity
			""", nativeQuery = true)
	int sellReserved(UUID productId, int quantity);

	@Modifying
	@Query(value = """
			update orders.inventory set on_hand_quantity = on_hand_quantity + :quantity,
				row_version = row_version + 1, updated_at = statement_timestamp()
			where product_id = :productId and seller_id = :sellerId
			""", nativeQuery = true)
	int restock(UUID productId, UUID sellerId, int quantity);

	@Query(value = "select statement_timestamp()", nativeQuery = true)
	Instant databaseNow();

	default List<Availability> availability(List<UUID> productIds) {
		return findByProductIdInOrderByProductId(productIds).stream()
				.map(row -> new Availability(row.getProductId(), row.availableQuantity())).toList();
	}

	default List<Details> details(List<UUID> productIds) {
		return findByProductIdInOrderByProductId(productIds).stream()
				.map(row -> new Details(row.getProductId(), row.getOnHandQuantity(), row.getReservedQuantity(),
						row.availableQuantity(), row.getRowVersion(), row.getUpdatedAt())).toList();
	}

	record Availability(UUID productId, int availableQuantity) {
	}

	record Details(UUID productId, int onHandQuantity, int reservedQuantity, int availableQuantity,
			long version, OffsetDateTime updatedAt) {
	}
}
