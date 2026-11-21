package com.example.order_service.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.time.Instant;
import java.util.UUID;

@FeignClient(name = "payment-service", contextId = "paymentCheckout", url = "${order.payment.base-url}")
public interface PaymentCheckoutClient {
	@GetMapping("/internal/v1/payments/by-checkout/{checkoutSessionId}")
	Envelope read(@PathVariable("checkoutSessionId") UUID checkoutSessionId,
			@RequestHeader("X-Service-Token") String credential);

	@PostMapping("/internal/v1/payments/checkouts/{checkoutSessionId}/provider-session")
	Envelope create(@PathVariable("checkoutSessionId") UUID checkoutSessionId,
			@RequestHeader("X-Service-Token") String credential);

	record Envelope(@JsonProperty(required = true) Collection data) {}

	record Collection(UUID paymentId, UUID checkoutSessionId, Status status, String paymentUrl, Instant expiresAt,
			Instant collectionClosedAt, boolean isSimulated) {}

	enum Status { PENDING, SUCCESS, FAILED }
}
