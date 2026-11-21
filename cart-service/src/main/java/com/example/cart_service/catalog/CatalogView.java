package com.example.cart_service.catalog;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Current product facts for a set of requested product ids, read through the owning services' APIs. Not stored:
 * price, sellability and stock are recomputed on every cart read (API §6.3, Database §3.3.2).
 */
public final class CatalogView {

	private final Set<UUID> requested;

	private final Map<UUID, ProductInfo> products;

	CatalogView(Collection<UUID> requested, Map<UUID, ProductInfo> products) {
		this.requested = Set.copyOf(requested);
		this.products = Map.copyOf(products);
	}

	public static CatalogView empty() {
		return new CatalogView(Set.of(), Map.of());
	}

	/** Whether this view has an answer (known or unknown product) for the id. */
	public boolean covers(UUID productId) {
		return requested.contains(productId);
	}

	/** Empty when Product does not know the id. */
	public Optional<ProductInfo> product(UUID productId) {
		return Optional.ofNullable(products.get(productId));
	}

	public boolean sellable(UUID productId) {
		return product(productId).map(ProductInfo::sellable).orElse(false);
	}

	public CatalogView merge(CatalogView other) {
		Set<UUID> ids = new HashSet<>(requested);
		ids.addAll(other.requested);
		Map<UUID, ProductInfo> all = new HashMap<>(products);
		all.putAll(other.products);
		return new CatalogView(ids, all);
	}

	/**
	 * @param sellable G-06: the product is SELLING and its seller may sell
	 * @param availableQuantity Order's on hand − reserved, at least 0
	 */
	public record ProductInfo(UUID productId, UUID sellerId, String sellerName, String productName,
			String coverImageUrl, long unitPriceVnd, boolean sellable, int availableQuantity) {
	}

}
