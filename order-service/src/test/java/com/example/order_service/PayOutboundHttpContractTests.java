package com.example.order_service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PayOutboundHttpContractTests extends PayHttpTestSupport {
	@Test
	void identityTouchFailureDoesNotUndoCommittedSession() throws Exception {
		var sample = sample(4, 2);
		touchStatus.set(500);
		var reply = pay(sample.customer(), UUID.randomUUID(), quote(sample), sample.address());
		assertThat(reply.statusCode()).isEqualTo(202);
		var id = UUID.fromString(body(reply).path("data").path("checkoutSessionId").asString());
		assertThat(lastTouch.get()).isEqualTo(id);
		assertThat(touchSawCommit.get()).isTrue();
		assertThat(jdbc.queryForObject("select count(*) from orders.checkout_outbox where checkout_session_id = ?",
				Integer.class, id)).isEqualTo(2);
	}

	@Test
	void financeFailureIs503AndDoesNotCreateOrTouchCheckout() throws Exception {
		var sample = sample(4, 2);
		financeStatus.set(500);
		assertRejected(pay(sample.customer(), UUID.randomUUID(), quote(sample), sample.address()),
				503, "SERVICE_UNAVAILABLE");
		assertNoCheckout(sample);
	}
}
