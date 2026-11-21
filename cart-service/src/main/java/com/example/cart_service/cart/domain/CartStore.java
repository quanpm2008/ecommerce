package com.example.cart_service.cart.domain;

import com.example.cart_service.common.error.ApiException;
import com.example.cart_service.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Local transactions on Cart's own tables. No remote call happens inside these methods (engineering.md: HTTP
 * outside write transactions). Every change locks the customer's cart row first, so limits are checked atomically
 * even for concurrent requests from several tabs (Database §3.3.2).
 */
@Service
public class CartStore {

	private final CartRepository carts;

	private final CartItemRepository items;

	private final Clock clock;

	public CartStore(CartRepository carts, CartItemRepository items, Clock clock) {
		this.carts = carts;
		this.items = items;
		this.clock = clock;
	}

	public record Contents(UUID cartId, List<CartItem> items) {
	}

	public record Totals(long lineCount, long totalQuantity) {
	}

	@Transactional
	public Contents contents(UUID customerId) {
		Cart cart = ensure(customerId, false);
		return new Contents(cart.getCartId(), items.findByCartIdOrderByCreatedAtAscCartItemIdAsc(cart.getCartId()));
	}

	@Transactional(readOnly = true)
	public Totals totals(UUID customerId) {
		return carts.findByCustomerId(customerId)
				.map(cart -> new Totals(items.countByCartId(cart.getCartId()),
						items.sumQuantityByCartId(cart.getCartId())))
				.orElse(new Totals(0, 0));
	}

	/** Adds to the existing line of the product, or opens a new line (P-10). */
	@Transactional
	public void add(UUID customerId, UUID productId, int quantity) {
		Cart cart = ensure(customerId, true);
		Instant now = clock.instant();
		Optional<CartItem> existing = items.findByCartIdAndProductId(cart.getCartId(), productId);
		if (existing.isPresent()) {
			CartItem line = existing.get();
			if (line.getQuantity() + quantity > CartLimits.MAX_QUANTITY_PER_LINE) {
				throw CartLimits.perLineExceeded(line.getQuantity());
			}
			line.changeQuantity(line.getQuantity() + quantity, now);
		}
		else {
			if (items.countByCartId(cart.getCartId()) >= CartLimits.MAX_LINES) {
				throw CartLimits.linesExceeded();
			}
			items.save(new CartItem(cart.getCartId(), productId, quantity, now));
		}
		cart.touch(now);
	}

	@Transactional
	public void changeQuantity(UUID customerId, UUID cartItemId, int quantity) {
		Cart cart = ensure(customerId, true);
		CartItem line = items.findByCartItemIdAndCartId(cartItemId, cart.getCartId())
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
		if (line.getQuantity() != quantity) {
			Instant now = clock.instant();
			line.changeQuantity(quantity, now);
			cart.touch(now);
		}
	}

	@Transactional
	public void remove(UUID customerId, UUID cartItemId) {
		Cart cart = ensure(customerId, true);
		CartItem line = items.findByCartItemIdAndCartId(cartItemId, cart.getCartId())
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
		items.delete(line);
		cart.touch(clock.instant());
	}

	/** Deletes the given lines that still exist in the customer's cart and returns those deleted. */
	@Transactional
	public List<CartItem> removeLines(UUID customerId, Collection<UUID> cartItemIds) {
		Cart cart = ensure(customerId, true);
		List<CartItem> removed = new ArrayList<>();
		for (UUID cartItemId : cartItemIds) {
			items.findByCartItemIdAndCartId(cartItemId, cart.getCartId()).ifPresent(line -> {
				items.delete(line);
				removed.add(line);
			});
		}
		if (!removed.isEmpty()) {
			cart.touch(clock.instant());
		}
		return removed;
	}

	private Cart ensure(UUID customerId, boolean lock) {
		carts.insertIfAbsent(UUID.randomUUID(), customerId);
		Optional<Cart> cart = lock ? carts.lockByCustomerId(customerId) : carts.findByCustomerId(customerId);
		return cart.orElseThrow(() -> new IllegalStateException("Cart of " + customerId + " was not created"));
	}

}
