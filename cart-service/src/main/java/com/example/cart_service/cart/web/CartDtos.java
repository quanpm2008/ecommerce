package com.example.cart_service.cart.web;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Request and response bodies of the cart API (API §6.3 DTO table). */
public final class CartDtos {

	private CartDtos() {
	}

	/** API §4.2 CartLineStatus; computed on every read, never stored (Database §3.3.2). */
	public enum LineStatus {
		IN_STOCK, INSUFFICIENT_STOCK, OUT_OF_STOCK
	}

	public record CartView(UUID cartId, List<SellerGroup> groups, List<RemovedItem> removedItems, Summary summary,
			boolean paymentBlocked) {
	}

	/** Each group becomes one Order at checkout (UC-B03.MAIN-02). */
	public record SellerGroup(SellerRef seller, List<CartItemView> items) {
	}

	public record SellerRef(UUID sellerId, String sellerName) {
	}

	/** {@code unitPriceVnd} is the current price; the order price is fixed at checkout (UC-B03.ALT-03). */
	public record CartItemView(UUID cartItemId, UUID productId, String productName, String coverImageUrl,
			long unitPriceVnd, int quantity, int availableQuantity, LineStatus lineStatus, boolean selectable,
			long lineTotalVnd) {
	}

	/** A line dropped because its product can no longer be sold (UC-B03.ALT-02). */
	public record RemovedItem(UUID productId, String productName, String reason) {
	}

	public record Summary(long totalQuantity, long lineCount) {
	}

	public record AddItemRequest(@NotNull UUID productId, @NotNull Integer quantity) {
	}

	public record ChangeQuantityRequest(@NotNull Integer quantity) {
	}

}
