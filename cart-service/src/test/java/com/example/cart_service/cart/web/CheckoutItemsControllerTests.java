package com.example.cart_service.cart.web;

import com.example.cart_service.IntegrationTestSupport;
import com.example.cart_service.cart.domain.CartStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "cart.internal.order-token=checkout-order-fixture")
class CheckoutItemsControllerTests extends IntegrationTestSupport {
	private static final String PATH = "/internal/v1/cart/checkout-items";

	@Autowired CartStore carts;

	@Test
	void returnsStoredOwnerAndVersionOnlyForSelectedRows() throws Exception {
		UUID owner = UUID.randomUUID();
		UUID product = UUID.randomUUID();
		carts.add(owner, product, 2);
		var cart = carts.contents(owner);
		var line = cart.items().getFirst();
		carts.changeQuantity(owner, line.getCartItemId(), 3);

		mvc.perform(post(PATH).header("X-Service-Token", "checkout-order-fixture")
						.contentType("application/json").content(selection(owner, line.getCartItemId())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.cartId").value(cart.cartId().toString()))
				.andExpect(jsonPath("$.data.customerId").value(owner.toString()))
				.andExpect(jsonPath("$.data.items[0].productId").value(product.toString()))
				.andExpect(jsonPath("$.data.items[0].quantity").value(3))
				.andExpect(jsonPath("$.data.items[0].rowVersion").value(1));

		UUID anotherOwner = UUID.randomUUID();
		carts.add(anotherOwner, product, 1);
		mvc.perform(post(PATH).header("X-Service-Token", "checkout-order-fixture")
						.contentType("application/json").content(selection(anotherOwner, line.getCartItemId())))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
		mvc.perform(post(PATH).header("X-Service-Token", "other-service")
						.contentType("application/json").content(selection(owner, line.getCartItemId())))
				.andExpect(status().isUnauthorized());
	}

	private static String selection(UUID owner, UUID line) {
		return """
				{"customerId":"%s","cartItemIds":["%s"]}
				""".formatted(owner, line);
	}
}
