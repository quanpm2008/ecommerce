package com.example.order_service.repository;

import com.example.order_service.entity.CheckoutSession;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class PendingCheckoutReader {
	private final CheckoutSessionJpaRepository sessions;

	public PendingCheckoutReader(CheckoutSessionJpaRepository sessions) {
		this.sessions = sessions;
	}

	public Optional<UUID> pendingSession(UUID customerId) {
		return sessions.findByCustomerIdAndStatus(customerId, CheckoutSession.Status.AWAITING_PAYMENT)
				.map(CheckoutSession::getCheckoutSessionId);
	}
}
