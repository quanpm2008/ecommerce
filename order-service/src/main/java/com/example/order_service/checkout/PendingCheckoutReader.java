package com.example.order_service.checkout;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class PendingCheckoutReader {
	private final JdbcTemplate jdbc;

	public PendingCheckoutReader(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<UUID> pendingSession(UUID customerId) {
		return jdbc.queryForList("""
				select checkout_session_id from orders.checkout_session
				where customer_id = ? and status = 'AWAITING_PAYMENT'
				""", UUID.class, customerId).stream().findFirst();
	}
}
