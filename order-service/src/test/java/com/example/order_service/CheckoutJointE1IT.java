package com.example.order_service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static com.example.order_service.OrderRealServiceE1Support.required;
import static org.assertj.core.api.Assertions.assertThat;

class CheckoutJointE1IT {
	@Test
	void reachesCommittedBuyNowWithRealIdentityAndProduct() throws Exception {
		try (var e1 = new OrderRealServiceE1Support("checkout-joint")) {
			e1.note("joint Identity=real Product=real Finance=contract-mock");
			e1.requireInputs("E1_ORDER_URL", "E1_IDENTITY_URL", "E1_PRODUCT_URL", "E1_IDENTITY_KIND",
					"E1_PRODUCT_KIND", "E1_CUSTOMER_BEARER", "E1_PRODUCT_ID", "E1_ADDRESS_ID");
			assertThat("real".equals(required("E1_IDENTITY_KIND")) && "real".equals(required("E1_PRODUCT_KIND")))
					.as("Joint E1 requires both real providers").isTrue();
			e1.revision("E1_ORDER_REVISION");
			e1.revision("E1_IDENTITY_REVISION");
			e1.revision("E1_PRODUCT_REVISION");
			String customer = required("E1_CUSTOMER_BEARER");
			var preview = e1.request("E1_ORDER_URL", "POST", "/api/v1/orders/checkout/preview", customer, null,
					Map.of("source", "BUY_NOW", "buyNowItem", Map.of("productId", UUID.fromString(required("E1_PRODUCT_ID")),
							"quantity", 1)), 200).path("data");
			assertThat(preview.path("quoteToken").asString(null) != null).as("Preview supplied a signed quote").isTrue();
			var payload = Map.of("quoteToken", preview.path("quoteToken").asString(),
					"addressId", UUID.fromString(required("E1_ADDRESS_ID")));
			var key = Map.of("Idempotency-Key", UUID.randomUUID().toString());
			var pay = e1.request("E1_ORDER_URL", "POST", "/api/v1/orders/checkout/pay", customer, null, payload, 202, key)
					.path("data");
			assertThat(pay.path("status").asString()).isEqualTo("AWAITING_PAYMENT");
			assertThat(pay.path("orders").size()).isEqualTo(1);
			var replay = e1.request("E1_ORDER_URL", "POST", "/api/v1/orders/checkout/pay", customer, null, payload, 202, key)
					.path("data");
			assertThat(replay.path("checkoutSessionId").equals(pay.path("checkoutSessionId")))
					.as("Replay returned the committed session").isTrue();
			e1.note("PASS checkout Preview, Pay and replay");
		}
	}
}
