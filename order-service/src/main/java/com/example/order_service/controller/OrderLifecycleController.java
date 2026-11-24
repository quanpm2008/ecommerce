package com.example.order_service.controller;

import com.example.order_service.exception.OrderApiExceptionHandler;
import com.example.order_service.security.OrderAuthorization.CurrentUser;
import com.example.order_service.service.OrderLifecycleDetail;
import com.example.order_service.service.OrderLifecycleService;
import com.example.order_service.service.OrderLifecycleService.Rejected;
import com.example.order_service.service.OrderLifecycleService.SellerAction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
class OrderLifecycleController {
	private final OrderLifecycleService lifecycle;
	private final OrderLifecycleDetail details;
	private final OrderApiExceptionHandler errors;

	OrderLifecycleController(OrderLifecycleService lifecycle, OrderLifecycleDetail details, OrderApiExceptionHandler errors) {
		this.lifecycle = lifecycle;
		this.details = details;
		this.errors = errors;
	}

	@GetMapping("/me/{id}")
	Envelope customer(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser user) {
		return new Envelope(details.customer(id, user, false));
	}

	@PostMapping("/me/{id}/cancel")
	Envelope cancel(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser user, @RequestBody Reason reason) {
		lifecycle.customerCancel(id, user.userId(), reason.reasonCode(), reason.reasonNote());
		return new Envelope(details.customer(id, user, true));
	}

	@PostMapping("/me/{id}/confirm-receipt")
	Envelope confirm(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser user) {
		lifecycle.customerConfirm(id, user.userId());
		return new Envelope(details.customer(id, user, true));
	}

	@GetMapping("/seller/orders/{id}")
	Envelope seller(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser user) {
		return new Envelope(details.seller(id, user, false));
	}

	@PostMapping("/seller/orders/{id}/start-packing")
	Envelope pack(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser user) {
		return sellerAction(id, user, SellerAction.PACK, new Reason(null, null));
	}

	@PostMapping("/seller/orders/{id}/mark-delivered")
	Envelope deliver(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser user) {
		return sellerAction(id, user, SellerAction.DELIVER, new Reason(null, null));
	}

	@PostMapping("/seller/orders/{id}/reject")
	Envelope reject(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser user, @RequestBody Reason reason) {
		return sellerAction(id, user, SellerAction.REJECT, reason);
	}

	private Envelope sellerAction(UUID id, CurrentUser user, SellerAction action, Reason reason) {
		lifecycle.sellerAction(id, user.userId(), action, reason.reasonCode(), reason.reasonNote());
		return new Envelope(details.seller(id, user, true));
	}

	@ExceptionHandler(Rejected.class)
	void rejected(Rejected failure, HttpServletRequest request, HttpServletResponse response) throws IOException {
		List<?> details = failure.current() == null ? List.of() : List.of(Map.of("currentStatus", failure.current()));
		errors.write(request, response, failure.status(), failure.getMessage(), "Yêu cầu không thể thực hiện", details);
	}

	record Reason(String reasonCode, String reasonNote) {}
	record Envelope(Map<String, Object> data) {}
}
