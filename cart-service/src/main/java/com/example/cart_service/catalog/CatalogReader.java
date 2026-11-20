package com.example.cart_service.catalog;

import com.example.cart_service.client.IdentityGateway;
import com.example.cart_service.client.InventoryGateway;
import com.example.cart_service.client.ProductGateway;
import com.example.cart_service.client.ProductSnapshot;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds a {@link CatalogView} from Product (price, name, status), Identity (seller G-06 and shop name) and Order
 * (available stock). Sellability is decided here from Product's status and Identity's seller status, each read from
 * its owner; a failing source makes the whole read fail with 503 instead of treating lines as unsellable.
 */
@Service
public class CatalogReader {

	private final ProductGateway products;

	private final IdentityGateway identity;

	private final InventoryGateway inventory;

	public CatalogReader(ProductGateway products, IdentityGateway identity, InventoryGateway inventory) {
		this.products = products;
		this.identity = identity;
		this.inventory = inventory;
	}

	public CatalogView read(Collection<UUID> productIds) {
		Set<UUID> ids = new LinkedHashSet<>(productIds);
		if (ids.isEmpty()) {
			return CatalogView.empty();
		}
		Map<UUID, ProductSnapshot> snapshots = products.products(ids);
		Set<UUID> sellerIds = snapshots.values().stream()
				.map(ProductSnapshot::sellerId)
				.filter(java.util.Objects::nonNull)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		Map<UUID, Boolean> sellerSellable = identity.sellable(sellerIds);
		Map<UUID, Integer> available = inventory.availability(snapshots.keySet());
		Set<UUID> sellableSellers = sellerIds.stream()
				.filter(id -> Boolean.TRUE.equals(sellerSellable.get(id)))
				.collect(Collectors.toCollection(LinkedHashSet::new));
		Map<UUID, String> sellerNames = identity.sellerNames(sellableSellers);

		Map<UUID, CatalogView.ProductInfo> infos = new HashMap<>();
		snapshots.forEach((id, product) -> {
			boolean sellable = product.selling() && product.priceVnd() != null
					&& sellableSellers.contains(product.sellerId());
			infos.put(id, new CatalogView.ProductInfo(id, product.sellerId(), sellerNames.get(product.sellerId()),
					product.productName(), product.coverImageUrl(),
					product.priceVnd() == null ? 0 : product.priceVnd(), sellable, available.getOrDefault(id, 0)));
		});
		return new CatalogView(ids, infos);
	}

}
