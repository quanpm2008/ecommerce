package com.example.order_service.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "payment-service", contextId = "paymentCheckout", url = "${order.payment.base-url}")
public interface PaymentCheckoutClient {
	@GetMapping("/internal/v1/payments/by-checkout/{checkoutSessionId}")
	Envelope read(@PathVariable("checkoutSessionId") UUID checkoutSessionId,
			@RequestHeader("X-Service-Token") String credential);

	@PostMapping("/internal/v1/payments/checkouts/{checkoutSessionId}/provider-session")
	Envelope create(@PathVariable("checkoutSessionId") UUID checkoutSessionId,
			@RequestHeader("X-Service-Token") String credential);

	@GetMapping("/internal/v1/payments/refunds")
	Refunds refunds(@RequestParam("orderIds") UUID orderId, @RequestHeader("X-Service-Token") String credential);

	@GetMapping("/internal/v1/payments/refunds")
	Refunds refundsByOrders(@RequestParam("orderIds") List<UUID> orderIds,
			@RequestHeader("X-Service-Token") String credential);

	@GetMapping("/internal/v1/payments/refunds")
	Refunds refundsByCheckouts(@RequestParam("checkoutSessionIds") List<UUID> checkoutSessionIds,
			@RequestHeader("X-Service-Token") String credential);

	record Refunds(@JsonProperty(required = true) List<Refund> data) {}

	record Refund(UUID refundId, UUID paymentId, UUID orderId, String scope, long amountVnd, String reason,
			String status, int retryCycle, int retryCount, boolean customerRetryRequired, boolean canRetryRefund,
			String providerRefundId, Instant createdAt, Instant completedAt, boolean isSimulated) {
		public Refund withRetryPermission(boolean allowed) {
			return new Refund(refundId, paymentId, orderId, scope, amountVnd, reason, status, retryCycle, retryCount,
					customerRetryRequired, allowed && canRetryRefund, providerRefundId, createdAt, completedAt, isSimulated);
		}
	}

	record Envelope(@JsonProperty(required = true) Collection data) {}

	record Collection(UUID paymentId, UUID checkoutSessionId, Status status, String paymentUrl, Instant expiresAt,
			Instant collectionClosedAt, boolean isSimulated, UUID customerId, Long amountVnd, String currency,
			Instant completedAt) {}

	enum Status { PENDING, SUCCESS, FAILED }
}
