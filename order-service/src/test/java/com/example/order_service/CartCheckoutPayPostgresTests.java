package com.example.order_service;

import com.example.order_service.service.CheckoutQuote.CartLine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CartCheckoutPayPostgresTests extends PayHttpTestSupport {
	@Test
	void commitsCartSnapshot() throws Exception {
		var sample = sample(8, 2);
		UUID cartId = UUID.randomUUID();
		UUID cartItemId = UUID.randomUUID();
		String quote = quotes.issueCart(sample.customer(), cartId,
				List.of(new CartLine(cartItemId, sample.product(), sample.quantity(), 7, SELLER, 1000)));
		var response = pay(sample.customer(), UUID.randomUUID(), quote, sample.address());
		assertThat(response.statusCode()).as(response.body()).isEqualTo(202);
		UUID sessionId = UUID.fromString(body(response).at("/data/checkoutSessionId").asString());
		assertThat(jdbc.queryForObject("select source_cart_id from orders.checkout_session where checkout_session_id = ?",
				UUID.class, sessionId)).isEqualTo(cartId);
	}
}
