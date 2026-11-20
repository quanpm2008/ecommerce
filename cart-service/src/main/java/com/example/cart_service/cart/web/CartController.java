package com.example.cart_service.cart.web;

import com.example.cart_service.cart.CartService;
import com.example.cart_service.cart.web.CartDtos.AddItemRequest;
import com.example.cart_service.cart.web.CartDtos.CartView;
import com.example.cart_service.cart.web.CartDtos.ChangeQuantityRequest;
import com.example.cart_service.cart.web.CartDtos.Summary;
import com.example.cart_service.common.web.ApiResponse;
import com.example.cart_service.security.CurrentCustomer;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Cart API (API §6.3), all under {@code cart:manage:own}. The owner is always the verified caller
 * ({@link CurrentCustomer}); a line of another customer's cart is 404. Selecting lines for checkout is front-end
 * state sent to Order, not stored here.
 */
@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

	private final CartService carts;

	public CartController(CartService carts) {
		this.carts = carts;
	}

	@GetMapping
	ApiResponse<CartView> view(@RequestAttribute(CurrentCustomer.ATTRIBUTE) CurrentCustomer customer) {
		return ApiResponse.of(carts.view(customer));
	}

	@GetMapping("/summary")
	ApiResponse<Summary> summary(@RequestAttribute(CurrentCustomer.ATTRIBUTE) CurrentCustomer customer) {
		return ApiResponse.of(carts.summary(customer));
	}

	@PostMapping("/items")
	ApiResponse<CartView> add(@RequestAttribute(CurrentCustomer.ATTRIBUTE) CurrentCustomer customer,
			@Valid @RequestBody AddItemRequest body) {
		return ApiResponse.of(carts.add(customer, body.productId(), body.quantity()));
	}

	@PatchMapping("/items/{cartItemId}")
	ApiResponse<CartView> changeQuantity(@RequestAttribute(CurrentCustomer.ATTRIBUTE) CurrentCustomer customer,
			@PathVariable UUID cartItemId, @Valid @RequestBody ChangeQuantityRequest body) {
		return ApiResponse.of(carts.changeQuantity(customer, cartItemId, body.quantity()));
	}

	@DeleteMapping("/items/{cartItemId}")
	ApiResponse<CartView> remove(@RequestAttribute(CurrentCustomer.ATTRIBUTE) CurrentCustomer customer,
			@PathVariable UUID cartItemId) {
		return ApiResponse.of(carts.remove(customer, cartItemId));
	}

}
