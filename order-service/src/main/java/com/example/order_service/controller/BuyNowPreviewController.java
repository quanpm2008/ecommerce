package com.example.order_service.controller;

import com.example.order_service.client.CartCheckoutItemsClient;
import com.example.order_service.client.CheckoutIdentityClient;
import com.example.order_service.client.ProductBatchClient;
import com.example.order_service.exception.OrderApiExceptionHandler;
import com.example.order_service.security.OrderAuthorization.CurrentUser;
import com.example.order_service.service.BuyNowPreviewService;
import com.example.order_service.service.CheckoutQuote;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@RestController
public class BuyNowPreviewController {
	private final DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();
	private final BuyNowPreviewService service;
	private final OrderApiExceptionHandler errors;

	public BuyNowPreviewController(BuyNowPreviewService service, OrderApiExceptionHandler errors) {
		this.service = service;
		this.errors = errors;
	}

	@PostMapping("/api/v1/orders/checkout/preview")
	Response preview(@AuthenticationPrincipal CurrentUser customer, HttpServletRequest request,
			@RequestBody PreviewRequest preview) {
		if ("CART".equals(preview.source())) {
			var selected = preview.cartItemIds();
			if (preview.buyNowItem() != null || selected == null || selected.isEmpty()
					|| selected.size() > CartCheckoutItemsClient.MAX_LINES || selected.contains(null)
					|| new HashSet<>(selected).size() != selected.size()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
			}
			return new Response(service.previewCart(customer, bearer.resolve(request), selected));
		}
		BuyNowItem item = preview.buyNowItem();
		if (!"BUY_NOW".equals(preview.source()) || preview.cartItemIds() != null || item == null
				|| item.productId() == null || item.quantity() == null || item.quantity() < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		}
		return new Response(service.preview(customer, bearer.resolve(request), item.productId(), item.quantity()));
	}

	@ExceptionHandler(CartCheckoutItemsClient.MissingItems.class)
	void missingItems(HttpServletRequest request, HttpServletResponse response) throws IOException {
		errors.write(request, response, 404, "NOT_FOUND", "Không tìm thấy các dòng giỏ hàng đã chọn");
	}

	@ExceptionHandler({CheckoutIdentityClient.Unavailable.class, ProductBatchClient.ProductBatchUnavailable.class,
			CartCheckoutItemsClient.Unavailable.class, CheckoutQuote.Unavailable.class, DataAccessException.class,
			TransactionException.class})
	void unavailable(HttpServletRequest request, HttpServletResponse response) throws IOException {
		errors.write(request, response, 503, "SERVICE_UNAVAILABLE", "Chưa thể xác nhận dữ liệu checkout");
	}

	record PreviewRequest(String source, BuyNowItem buyNowItem, List<UUID> cartItemIds) {
	}

	record BuyNowItem(UUID productId, Integer quantity) {
	}

	record Response(BuyNowPreviewService.Preview data) {
	}
}
