package com.example.cart_service.cart;

import com.example.cart_service.cart.domain.CartItem;
import com.example.cart_service.cart.domain.CartLimits;
import com.example.cart_service.cart.domain.CartStore;
import com.example.cart_service.cart.web.CartDtos.CartItemView;
import com.example.cart_service.cart.web.CartDtos.CartView;
import com.example.cart_service.cart.web.CartDtos.LineStatus;
import com.example.cart_service.cart.web.CartDtos.RemovedItem;
import com.example.cart_service.cart.web.CartDtos.SellerGroup;
import com.example.cart_service.cart.web.CartDtos.SellerRef;
import com.example.cart_service.cart.web.CartDtos.Summary;
import com.example.cart_service.catalog.CatalogReader;
import com.example.cart_service.catalog.CatalogView;
import com.example.cart_service.catalog.CatalogView.ProductInfo;
import com.example.cart_service.common.error.ApiException;
import com.example.cart_service.common.error.ErrorCode;
import com.example.cart_service.security.CurrentCustomer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * UC-B03. Every operation that returns the cart first reads the catalog for the products involved (outside any
 * transaction), then changes the cart in one local transaction, then assembles the response from what was already
 * read. So a dependency outage fails the request before anything is written: a client retry cannot add twice.
 */
@Service
public class CartService {

	static final String REASON_NOT_SELLABLE = "PRODUCT_NOT_SELLABLE";

	private final CartStore store;

	private final CatalogReader catalog;

	public CartService(CartStore store, CatalogReader catalog) {
		this.store = store;
		this.catalog = catalog;
	}

	public CartView view(CurrentCustomer customer) {
		CartStore.Contents contents = store.contents(customer.customerId());
		return assemble(customer, catalog.read(productIds(contents.items())));
	}

	/** Navbar counter: local data only, no call to another service (API §6.3). */
	public Summary summary(CurrentCustomer customer) {
		CartStore.Totals totals = store.totals(customer.customerId());
		return new Summary(totals.totalQuantity(), totals.lineCount());
	}

	/**
	 * Adds {@code quantity} of a sellable product. An out-of-stock product may be added (stock is checked at
	 * checkout); an unsellable or unknown one is PRODUCT_NOT_SELLABLE. Nothing is reserved.
	 */
	public CartView add(CurrentCustomer customer, UUID productId, int quantity) {
		CartLimits.requireLineQuantity(quantity);
		Set<UUID> ids = productIds(store.contents(customer.customerId()).items());
		ids.add(productId);
		CatalogView view = catalog.read(ids);
		if (!view.sellable(productId)) {
			throw new ApiException(ErrorCode.PRODUCT_NOT_SELLABLE);
		}
		store.add(customer.customerId(), productId, quantity);
		return assemble(customer, view);
	}

	public CartView changeQuantity(CurrentCustomer customer, UUID cartItemId, int quantity) {
		CartLimits.requireLineQuantity(quantity);
		CatalogView view = catalog.read(productIds(store.contents(customer.customerId()).items()));
		store.changeQuantity(customer.customerId(), cartItemId, quantity);
		return assemble(customer, view);
	}

	public CartView remove(CurrentCustomer customer, UUID cartItemId) {
		CatalogView view = catalog.read(productIds(store.contents(customer.customerId()).items()));
		store.remove(customer.customerId(), cartItemId);
		return assemble(customer, view);
	}

	/**
	 * Builds the response from the current lines. Lines whose product can no longer be sold are deleted and reported
	 * in {@code removedItems} (UC-B03.ALT-02). A line added meanwhile by another tab is read from the catalog too.
	 */
	private CartView assemble(CurrentCustomer customer, CatalogView view) {
		CartStore.Contents contents = store.contents(customer.customerId());
		Set<UUID> missing = new LinkedHashSet<>();
		for (CartItem line : contents.items()) {
			if (!view.covers(line.getProductId())) {
				missing.add(line.getProductId());
			}
		}
		if (!missing.isEmpty()) {
			view = view.merge(catalog.read(missing));
		}

		Set<UUID> unsellable = new HashSet<>();
		for (CartItem line : contents.items()) {
			if (!view.sellable(line.getProductId())) {
				unsellable.add(line.getCartItemId());
			}
		}
		List<RemovedItem> removedItems = new ArrayList<>();
		if (!unsellable.isEmpty()) {
			for (CartItem line : store.removeLines(customer.customerId(), unsellable)) {
				String name = view.product(line.getProductId()).map(ProductInfo::productName).orElse(null);
				removedItems.add(new RemovedItem(line.getProductId(), name, REASON_NOT_SELLABLE));
			}
		}

		Map<UUID, List<CartItemView>> bySeller = new LinkedHashMap<>();
		Map<UUID, String> sellerNames = new LinkedHashMap<>();
		long totalQuantity = 0;
		long lineCount = 0;
		for (CartItem line : contents.items()) {
			if (unsellable.contains(line.getCartItemId())) {
				continue;
			}
			ProductInfo product = view.product(line.getProductId()).orElseThrow();
			bySeller.computeIfAbsent(product.sellerId(), id -> new ArrayList<>()).add(itemView(line, product));
			sellerNames.putIfAbsent(product.sellerId(), product.sellerName());
			totalQuantity += line.getQuantity();
			lineCount++;
		}
		List<SellerGroup> groups = new ArrayList<>();
		bySeller.forEach((sellerId, lines) ->
				groups.add(new SellerGroup(new SellerRef(sellerId, sellerNames.get(sellerId)), List.copyOf(lines))));
		return new CartView(contents.cartId(), groups, removedItems, new Summary(totalQuantity, lineCount),
				customer.paymentBlocked());
	}

	static CartItemView itemView(CartItem line, ProductInfo product) {
		LineStatus status = lineStatus(product.availableQuantity(), line.getQuantity());
		return new CartItemView(line.getCartItemId(), line.getProductId(), product.productName(),
				product.coverImageUrl(), product.unitPriceVnd(), line.getQuantity(), product.availableQuantity(),
				status, status == LineStatus.IN_STOCK, product.unitPriceVnd() * line.getQuantity());
	}

	/** Database §3.3.2: enough stock, some but not enough ("only X left"), or none. Only IN_STOCK is selectable. */
	static LineStatus lineStatus(int available, int quantity) {
		if (available >= quantity) {
			return LineStatus.IN_STOCK;
		}
		return available > 0 ? LineStatus.INSUFFICIENT_STOCK : LineStatus.OUT_OF_STOCK;
	}

	private static Set<UUID> productIds(List<CartItem> lines) {
		Set<UUID> ids = new LinkedHashSet<>();
		lines.forEach(line -> ids.add(line.getProductId()));
		return ids;
	}

}
