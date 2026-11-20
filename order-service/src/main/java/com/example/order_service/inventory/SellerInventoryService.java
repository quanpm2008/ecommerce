package com.example.order_service.inventory;

import com.example.order_service.client.ProductBatchClient;
import com.example.order_service.client.ProductBatchClient.ProductInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class SellerInventoryService {
	private final SellerInventoryRepository inventory;
	private final ProductBatchClient products;
	private final TransactionTemplate updates;

	SellerInventoryService(SellerInventoryRepository inventory, ProductBatchClient products,
			PlatformTransactionManager transactionManager) {
		this.inventory = inventory;
		this.products = products;
		this.updates = new TransactionTemplate(transactionManager);
	}

	List<Item> list(UUID seller, List<UUID> ids, int page, int size) {
		var rows = inventory.list(seller, ids, offset(page, size), size);
		if (rows.isEmpty()) {
			return List.of();
		}
		var names = products.getProducts(rows.stream().map(SellerInventoryRepository.Stock::productId).toList()).stream()
				.collect(Collectors.toMap(ProductInfo::productId, Function.identity()));
		return rows.stream().map(stock -> item(stock, productName(names.get(stock.productId()), seller))).toList();
	}

	Item update(UUID seller, UUID product, long version, int quantity, String note) {
		ownedStock(seller, product, false);
		var productsForId = products.getProducts(List.of(product));
		String name = productName(productsForId.isEmpty() ? null : productsForId.getFirst(), seller);
		return updates.execute(status -> {
			var stock = ownedStock(seller, product, true);
			if (stock.version() != version) {
				throw new Rejected(409, "VERSION_CONFLICT", Map.of("current", item(stock, name)));
			}
			if (quantity < stock.reserved()) {
				throw new Rejected(422, "STOCK_BELOW_RESERVED", Map.of("minAllowed", stock.reserved()));
			}
			if (quantity == stock.onHand()) {
				return item(stock, name);
			}
			var updated = inventory.update(seller, product, quantity);
			inventory.recordAdjustment(seller, product, quantity - stock.onHand(), updated.version(), note);
			return item(updated, name);
		});
	}

	@Transactional(readOnly = true)
	MovementPage listMovements(UUID seller, UUID product, int page, int size) {
		ownedStock(seller, product, false);
		long count = inventory.movementCount(product);
		return new MovementPage(inventory.movements(product, offset(page, size), size),
				new PageMeta(page, size, count, (count + size - 1L) / size));
	}

	private SellerInventoryRepository.Stock ownedStock(UUID seller, UUID product, boolean lock) {
		return inventory.get(seller, product, lock).orElseThrow(
				() -> new Rejected(404, "NOT_FOUND", Map.of("resource", "inventory")));
	}

	private static long offset(int page, int size) {
		return (page - 1L) * size;
	}

	private static String productName(ProductInfo product, UUID seller) {
		if (product == null || !seller.equals(product.sellerId())) {
			throw new Rejected(503, "SERVICE_UNAVAILABLE", Map.of());
		}
		return product.productName();
	}

	private static Item item(SellerInventoryRepository.Stock stock, String name) {
		return new Item(stock.productId(), name, stock.onHand(), stock.reserved(), stock.onHand() - stock.reserved(),
				stock.version(), stock.updatedAt());
	}

	record Item(UUID productId, String productName, int onHandQuantity, int reservedQuantity, int availableQuantity,
			long version, OffsetDateTime updatedAt) {
	}

	record MovementPage(List<SellerInventoryRepository.Movement> data, PageMeta meta) {
	}

	record PageMeta(int page, int size, long totalItems, long totalPages) {
	}

	static final class Rejected extends RuntimeException {
		final int status;
		final String code;
		final Map<String, ?> detail;

		Rejected(int status, String code, Map<String, ?> detail) {
			this.status = status;
			this.code = code;
			this.detail = detail;
		}
	}
}
