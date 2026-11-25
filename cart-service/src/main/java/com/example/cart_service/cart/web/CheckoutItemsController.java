package com.example.cart_service.cart.web;

import com.example.cart_service.cart.domain.CartStore;
import com.example.cart_service.common.error.ApiException;
import com.example.cart_service.common.error.ErrorCode;
import com.example.cart_service.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@RestController
class CheckoutItemsController {
	private final CartStore carts;
	private final String orderToken;

	CheckoutItemsController(CartStore carts, @Value("${cart.internal.order-token:}") String orderToken) {
		this.carts = carts;
		this.orderToken = orderToken;
	}

	@PostMapping("/internal/v1/cart/checkout-items")
	ApiResponse<CartStore.CheckoutSnapshot> read(HttpServletRequest request, @Valid @RequestBody Selection selection) {
		var tokens = Collections.list(request.getHeaders("X-Service-Token"));
		if (orderToken.isBlank() || tokens.size() != 1 || !MessageDigest.isEqual(
				orderToken.getBytes(StandardCharsets.UTF_8), tokens.getFirst().getBytes(StandardCharsets.UTF_8))) {
			throw new ApiException(ErrorCode.UNAUTHENTICATED);
		}
		if (new HashSet<>(selection.cartItemIds()).size() != selection.cartItemIds().size()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR);
		}
		return ApiResponse.of(carts.checkoutItems(selection.customerId(), selection.cartItemIds()));
	}

	record Selection(@NotNull UUID customerId, @NotNull @Size(min = 1, max = 50) List<@NotNull UUID> cartItemIds) {}
}
