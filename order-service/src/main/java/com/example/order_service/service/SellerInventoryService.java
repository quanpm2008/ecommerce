package com.example.order_service.service;

import com.example.order_service.client.ProductBatchClient.ProductInfo;
import com.example.order_service.client.ProductBatchClient;
import com.example.order_service.exception.SellerInventoryRejected;
import com.example.order_service.entity.Inventory;
import com.example.order_service.entity.StockMovement;
import com.example.order_service.repository.InventoryStockRepository;
import com.example.order_service.repository.StockMovementRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SellerInventoryService {
	private final InventoryStockRepository inventory;
	private final StockMovementRepository movements;
	private final ProductBatchClient products;
	private final TransactionTemplate updates;

	SellerInventoryService(InventoryStockRepository inventory, StockMovementRepository movements,
			ProductBatchClient products,
			PlatformTransactionManager transactionManager) {
		this.inventory = inventory;
		this.movements = movements;
		this.products = products;
		this.updates = new TransactionTemplate(transactionManager);
	}

	public List<Item> list(UUID seller, List<UUID> ids, int page, int size) {
		var paging = PageRequest.of(page - 1, size);
		var rows = ids == null ? inventory.findBySellerIdOrderByUpdatedAtDescProductId(seller, paging)
				: inventory.findBySellerIdAndProductIdInOrderByUpdatedAtDescProductId(seller, ids, paging);
		if (rows.isEmpty()) {
			return List.of();
		}
		var names = products.getProducts(rows.stream().map(Inventory::getProductId).toList()).stream()
				.collect(Collectors.toMap(ProductInfo::productId, Function.identity()));
		return rows.stream().map(stock -> item(stock, productName(names.get(stock.getProductId()), seller))).toList();
	}

	public Item update(UUID seller, UUID product, long version, int quantity, String note) {
		ownedStock(seller, product, false);
		var productsForId = products.getProducts(List.of(product));
		String name = productName(productsForId.isEmpty() ? null : productsForId.getFirst(), seller);
		return updates.execute(status -> {
			var stock = ownedStock(seller, product, true);
			if (stock.getRowVersion() != version) {
				throw new SellerInventoryRejected(409, "VERSION_CONFLICT", Map.of("current", item(stock, name)));
			}
			if (quantity < stock.getReservedQuantity()) {
				throw new SellerInventoryRejected(422, "STOCK_BELOW_RESERVED", Map.of("minAllowed", stock.getReservedQuantity()));
			}
			if (quantity == stock.getOnHandQuantity()) {
				return item(stock, name);
			}
			int delta = quantity - stock.getOnHandQuantity();
			var now = inventory.databaseNow().atOffset(ZoneOffset.UTC);
			stock.adjustOnHand(quantity, now);
			inventory.flush();
			movements.save(new StockMovement(UUID.randomUUID(), product, StockMovement.Type.MANUAL_ADJUST,
					delta, seller, "manual:" + product + ":" + stock.getRowVersion(), now, StockMovement.ActorType.USER, note));
			return item(stock, name);
		});
	}

	@Transactional(readOnly = true)
	public MovementPage listMovements(UUID seller, UUID product, int page, int size) {
		ownedStock(seller, product, false);
		var results = movements.findByProductIdOrderByCreatedAtDescStockMovementId(product, PageRequest.of(page - 1, size));
		return new MovementPage(results.getContent().stream().map(movement -> new Movement(movement.getStockMovementId(),
				movement.getMovementType().name(), movement.getQuantityDelta(), movement.getOrderItemId(), null,
				movement.getActorType().name(), movement.getNote(), movement.getCreatedAt())).toList(),
				new PageMeta(page, size, results.getTotalElements(), results.getTotalPages()));
	}

	private Inventory ownedStock(UUID seller, UUID product, boolean lock) {
		var stock = lock ? inventory.lockOwned(seller, product) : inventory.findBySellerIdAndProductId(seller, product);
		return stock.orElseThrow(() -> new SellerInventoryRejected(404, "NOT_FOUND", Map.of("resource", "inventory")));
	}

	private static String productName(ProductInfo product, UUID seller) {
		if (product == null || !seller.equals(product.sellerId())) {
			throw new SellerInventoryRejected(503, "SERVICE_UNAVAILABLE", Map.of());
		}
		return product.productName();
	}

	private static Item item(Inventory stock, String name) {
		return new Item(stock.getProductId(), name, stock.getOnHandQuantity(), stock.getReservedQuantity(), stock.availableQuantity(),
				stock.getRowVersion(), stock.getUpdatedAt());
	}

	public record Item(UUID productId, String productName, int onHandQuantity, int reservedQuantity, int availableQuantity,
			long version, OffsetDateTime updatedAt) {
	}

	public record Movement(UUID movementId, String movementType, int quantityDelta, UUID orderItemId, UUID orderId,
			String actorType, String note, OffsetDateTime createdAt) {
	}

	public record MovementPage(List<Movement> data, PageMeta meta) {
	}

	public record PageMeta(int page, int size, long totalItems, long totalPages) {
	}
}
