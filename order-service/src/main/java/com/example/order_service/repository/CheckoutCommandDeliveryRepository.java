package com.example.order_service.repository;

import com.example.order_service.entity.CheckoutCommand;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class CheckoutCommandDeliveryRepository {
	private final EntityManager entities;

	CheckoutCommandDeliveryRepository(EntityManager entities) {
		this.entities = entities;
	}

	@Transactional
	public Optional<CheckoutCommand> claim(UUID token) {
		return entities.createNativeQuery("""
				with next_command as (
					select command_id from orders.checkout_outbox
					where delivered_at is null and next_attempt_at <= statement_timestamp()
						and (lease_until is null or lease_until <= statement_timestamp())
					order by next_attempt_at, created_at, command_id
					limit 1 for update skip locked
				)
				update orders.checkout_outbox o set lease_token = :token, lease_until = statement_timestamp() + interval '30 seconds'
				from next_command n where o.command_id = n.command_id
				returning o.command_id, o.checkout_session_id, o.command_type, o.expires_at, o.created_at
				""", CheckoutCommand.class).setParameter("token", token).getResultList().stream()
				.map(CheckoutCommand.class::cast).findFirst();
	}

	@Transactional
	public void acknowledge(UUID commandId, UUID token) {
		entities.createNativeQuery("""
				update orders.checkout_outbox set delivered_at = statement_timestamp(), lease_token = null, lease_until = null
				where command_id = :commandId and lease_token = :token
				""").setParameter("commandId", commandId).setParameter("token", token).executeUpdate();
	}

	@Transactional
	public void retry(UUID commandId, UUID token) {
		entities.createNativeQuery("""
				update orders.checkout_outbox set next_attempt_at = statement_timestamp() + interval '5 seconds',
					lease_token = null, lease_until = null
				where command_id = :commandId and lease_token = :token
				""").setParameter("commandId", commandId).setParameter("token", token).executeUpdate();
	}
}
