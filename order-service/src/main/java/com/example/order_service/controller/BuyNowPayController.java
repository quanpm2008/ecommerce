package com.example.order_service.controller;

import com.example.order_service.client.CheckoutIdentityClient;
import com.example.order_service.client.FinanceCommissionClient;
import com.example.order_service.client.PayIdentityClient;
import com.example.order_service.client.ProductBatchClient;
import com.example.order_service.dto.response.CheckoutResponse;
import com.example.order_service.exception.BuyNowPayRejected;
import com.example.order_service.exception.OrderApiExceptionHandler;
import com.example.order_service.repository.CheckoutCommitRepository;
import com.example.order_service.repository.CheckoutReplayRepository;
import com.example.order_service.security.OrderAuthorization.CurrentUser;
import com.example.order_service.service.BuyNowPayService;
import com.example.order_service.service.CheckoutQuote;
import com.example.order_service.service.CheckoutPaymentReadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

@RestController
class BuyNowPayController {
	private final BuyNowPayService payments;
	private final OrderApiExceptionHandler errors;
	private final CheckoutPaymentReadService reads;

	BuyNowPayController(BuyNowPayService payments, OrderApiExceptionHandler errors, CheckoutPaymentReadService reads) {
		this.payments = payments;
		this.errors = errors;
		this.reads = reads;
	}

	@PostMapping(CheckoutCommitRepository.PAY_ENDPOINT)
	ResponseEntity<Response> pay(@AuthenticationPrincipal CurrentUser user,
			@RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody PayRequest body) {
		if (key == null || key.isBlank()) {
			throw new BuyNowPayRejected(400, "IDEMPOTENCY_KEY_REQUIRED");
		}
		try {
			if (!UUID.fromString(key).toString().equalsIgnoreCase(key) || body.addressId() == null) {
				throw new IllegalArgumentException();
			}
		} catch (IllegalArgumentException invalid) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		}
		var result = payments.pay(user, UUID.fromString(key).toString(), body.quoteToken(), body.addressId());
		var data = result.replayed() ? reads.replay(result.response()) : result.response();
		boolean waitingForLink = "AWAITING_PAYMENT".equals(data.status()) && data.paymentUrl() == null;
		var response = ResponseEntity.status(waitingForLink ? 202 : 200);
		if (result.replayed()) {
			response.header("Idempotent-Replayed", "true");
		}
		return response.body(new Response(data));
	}

	@GetMapping("/api/v1/orders/checkout-sessions/current")
	Response current(@AuthenticationPrincipal CurrentUser user) {
		return new Response(reads.current(user.userId()));
	}

	@GetMapping("/api/v1/orders/checkout-sessions/{checkoutSessionId}")
	Response detail(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID checkoutSessionId) {
		return new Response(reads.detail(checkoutSessionId, user.userId()));
	}

	@GetMapping("/internal/v1/orders/checkout-sessions/{id}/payable")
	PayableResponse payable(@PathVariable UUID id) {
		return new PayableResponse(reads.payable(id));
	}

	@ExceptionHandler(BuyNowPayRejected.class)
	void rejected(BuyNowPayRejected failure, HttpServletRequest req, HttpServletResponse res) throws IOException {
		errors.write(req, res, failure.status(), failure.code(), "Không thể tạo phiên thanh toán");
	}

	@ExceptionHandler(CheckoutQuote.Invalid.class)
	void invalidQuote(HttpServletRequest req, HttpServletResponse res) throws IOException {
		errors.write(req, res, 400, "CHECKOUT_QUOTE_INVALID", "Quote không hợp lệ");
	}

	@ExceptionHandler(PayIdentityClient.AddressNotFound.class)
	void addressNotFound(HttpServletRequest req, HttpServletResponse res) throws IOException {
		errors.write(req, res, 404, "NOT_FOUND", "Không tìm thấy địa chỉ nhận hàng");
	}

	@ExceptionHandler({CheckoutIdentityClient.Unavailable.class, ProductBatchClient.ProductBatchUnavailable.class,
			CheckoutQuote.Unavailable.class, PayIdentityClient.Unavailable.class, FinanceCommissionClient.Unavailable.class})
	void unavailable(HttpServletRequest req, HttpServletResponse res) throws IOException {
		errors.write(req, res, 503, "SERVICE_UNAVAILABLE", "Chưa thể xác nhận dữ liệu checkout");
	}

	record PayRequest(String quoteToken, UUID addressId) {
	}

	record Response(CheckoutResponse data) {
	}

	record PayableResponse(CheckoutReplayRepository.Payable data) {}
}
