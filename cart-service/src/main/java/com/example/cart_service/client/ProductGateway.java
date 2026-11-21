package com.example.cart_service.client;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Current product data from Product. A product Product does not return is unknown. Source failure is 503. */
public interface ProductGateway {

	Map<UUID, ProductSnapshot> products(Collection<UUID> productIds);

}
