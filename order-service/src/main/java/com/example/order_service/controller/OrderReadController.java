package com.example.order_service.controller;

import com.example.order_service.exception.OrderApiExceptionHandler;
import com.example.order_service.security.OrderAuthorization.CurrentUser;
import com.example.order_service.service.OrderLifecycleService.Rejected;
import com.example.order_service.service.OrderReadService;
import com.example.order_service.service.OrderReadService.Filters;
import com.example.order_service.service.OrderReadService.View;
import com.example.order_service.entity.Order.Status;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
class OrderReadController {
	private final OrderReadService reads;
	private final OrderApiExceptionHandler errors;

	OrderReadController(OrderReadService reads, OrderApiExceptionHandler errors) {
		this.reads = reads;
		this.errors = errors;
	}

	@GetMapping("/me")
	Map<String, Object> customer(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) List<Status> status, @RequestParam(required = false) UUID checkoutSessionId,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int size) {
		return reads.search(View.CUSTOMER, user, new Filters(status, checkoutSessionId, null, null, null,
				q, null, null, null, page, size), null);
	}

	@GetMapping("/seller/orders")
	Map<String, Object> seller(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) List<Status> status, @RequestParam(required = false) String q,
			@RequestParam(required = false) Instant dueBefore, @RequestParam(required = false) String sort,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
		return reads.search(View.SELLER, user, new Filters(status, null, null, null, null, q, dueBefore, null,
				sort, page, size), null);
	}

	@GetMapping("/admin/orders")
	Map<String, Object> admin(@AuthenticationPrincipal CurrentUser user, @RequestHeader("Authorization") String bearer,
			@RequestParam(required = false) List<Status> status, @RequestParam(required = false) UUID orderId,
			@RequestParam(required = false) UUID checkoutSessionId, @RequestParam(required = false) UUID sellerId,
			@RequestParam(required = false) UUID customerId, @RequestParam(required = false) String overdue,
			@RequestParam(required = false) String sort, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int size) {
		return reads.search(View.ADMIN, user, new Filters(status, checkoutSessionId, orderId, sellerId, customerId,
				null, null, overdue, sort, page, size), bearer);
	}

	@GetMapping("/admin/orders/{id}")
	Map<String, Object> detail(@PathVariable UUID id, @RequestHeader("Authorization") String bearer) {
		return reads.adminDetail(id, bearer);
	}

	@ExceptionHandler(Rejected.class)
	void invalid(Rejected failure, HttpServletRequest request, HttpServletResponse response) throws IOException {
		errors.write(request, response, failure.status(), failure.getMessage(), failure.getMessage());
	}
}
