package com.example.order_service.checkout;

import com.example.order_service.client.PayIdentityClient.ShippingAddress;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CheckoutResponse(UUID checkoutSessionId, String source, String status, String closeReason,
		String paymentStatus, String paymentUrl, long totalAmountVnd, long shippingFeeVnd, Instant expiresAt,
		Instant paidAt, ShippingAddress shippingAddress, List<OrderSummary> orders, List<String> allowedActions,
		boolean isSimulated) {

	public record OrderSummary(UUID orderId, UUID checkoutSessionId, Seller seller, String status, long totalAmountVnd,
			int itemCount, FirstItem firstItem, Instant createdAt, Instant paidAt, String refundStatus) {
	}

	public record Seller(UUID sellerId, String sellerName) {
	}

	public record FirstItem(String productName, String coverImageUrl) {
	}
}
