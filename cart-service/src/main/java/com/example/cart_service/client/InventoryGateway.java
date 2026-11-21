package com.example.cart_service.client;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Available quantity (on hand − reserved) per product from Order. A product without an inventory record has 0
 * available. Source failure is 503, never 0.
 */
public interface InventoryGateway {

	Map<UUID, Integer> availability(Collection<UUID> productIds);

}
