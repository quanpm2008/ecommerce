package com.example.order_service.repository;

import com.example.order_service.entity.CheckoutEvent;
import com.example.order_service.entity.Reservation;
import com.example.order_service.entity.StockMovement;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CheckoutOutcomeRepository {
	private final EntityManager entities;

	CheckoutOutcomeRepository(EntityManager entities) {
		this.entities = entities;
	}

	public boolean receive(UUID inboxId, UUID checkoutId, String raw) {
		int inserted = entities.createNativeQuery("""
				insert into orders.checkout_inbox (event_id, checkout_session_id, raw_envelope, state)
				values (:inboxId, :checkoutId, :raw, 'RECEIVED') on conflict (event_id) do nothing
				""").setParameter("inboxId", inboxId).setParameter("checkoutId", checkoutId).setParameter("raw", raw)
				.executeUpdate();
		if (inserted == 0) {
			entities.createNativeQuery("""
					update orders.checkout_inbox set conflict_envelope = :raw, last_error = 'EVENT_ID_CONFLICT'
					where event_id = :inboxId and raw_envelope <> :raw and conflict_envelope is null
					""").setParameter("inboxId", inboxId).setParameter("raw", raw).executeUpdate();
		}
		return inserted == 1;
	}

	public void finish(UUID eventId, String state, String reason) {
		entities.createNativeQuery("""
				update orders.checkout_inbox set state = :state, last_error = :reason,
					resolved_at = case when :state = 'WAITING_DEPENDENCY' then null else statement_timestamp() end
				where event_id = :eventId
				""").setParameter("eventId", eventId).setParameter("state", state).setParameter("reason", reason).executeUpdate();
	}

	public boolean waitingForEvidence(UUID checkoutId) {
		return Boolean.TRUE.equals(entities.createNativeQuery("""
				select exists (select 1 from orders.checkout_inbox where checkout_session_id = :checkoutId
					and state = 'WAITING_DEPENDENCY')
				""").setParameter("checkoutId", checkoutId).getSingleResult());
	}

	public void closeWaits(UUID checkoutId) {
		entities.createNativeQuery("""
				update orders.checkout_inbox set state = 'FAILED_REVIEW', resolved_at = statement_timestamp(),
					last_error = 'CHECKOUT_EXPIRED_AWAITING_EVIDENCE'
				where checkout_session_id = :checkoutId and state = 'WAITING_DEPENDENCY'
				""").setParameter("checkoutId", checkoutId).executeUpdate();
	}

	public void markWaitsApplied(UUID checkoutId) {
		entities.createNativeQuery("""
				update orders.checkout_inbox set state = 'APPLIED', resolved_at = statement_timestamp(), last_error = null
				where checkout_session_id = :checkoutId and state = 'WAITING_DEPENDENCY'
				""").setParameter("checkoutId", checkoutId).executeUpdate();
	}

	public boolean matchesExpiry(UUID commandId, UUID checkoutId, Instant expiresAt) {
		return Boolean.TRUE.equals(entities.createNativeQuery("""
				select exists (select 1 from orders.checkout_outbox where command_id = :commandId
					and checkout_session_id = :checkoutId and expires_at = :expiresAt and command_type = 'ExpireCheckout')
				""").setParameter("commandId", commandId).setParameter("checkoutId", checkoutId)
				.setParameter("expiresAt", expiresAt).getSingleResult());
	}

	public List<Reservation> reservations(UUID checkoutId) {
		var reservations = entities.createQuery("""
				select r from Reservation r, OrderItem i, Order o
				where r.orderItemId = i.orderItemId and i.orderId = o.orderId and o.checkoutSessionId = :checkoutId
				""", Reservation.class).setParameter("checkoutId", checkoutId).getResultList();
		long items = entities.createQuery("""
				select count(i) from OrderItem i, Order o where i.orderId = o.orderId and o.checkoutSessionId = :checkoutId
				""", Long.class).setParameter("checkoutId", checkoutId).getSingleResult();
		if (items == 0 || items != reservations.size()) {
			throw new IllegalStateException("Checkout is missing one or more reservations");
		}
		return reservations;
	}

	public void enqueue(CheckoutEvent event) {
		entities.persist(event);
	}

	public void recordStockMovement(StockMovement movement) {
		entities.persist(movement);
	}

	@Transactional
	public Optional<CheckoutEvent> claim(UUID token) {
		return entities.createNativeQuery("""
				with next_event as (
					select event_id from orders.checkout_event_outbox where sent_at is null
						and next_attempt_at <= statement_timestamp()
						and (lease_until is null or lease_until <= statement_timestamp())
					order by next_attempt_at, created_at, event_id limit 1 for update skip locked
				)
				update orders.checkout_event_outbox o set lease_token = :token, lease_until = statement_timestamp() + interval '30 seconds'
				from next_event n where o.event_id = n.event_id
				returning o.event_id, o.checkout_session_id, o.order_id, o.order_version, o.envelope, o.created_at
				""", CheckoutEvent.class).setParameter("token", token).getResultList().stream()
				.map(CheckoutEvent.class::cast).findFirst();
	}

	@Transactional
	public void acknowledge(UUID eventId, UUID token) {
		entities.createNativeQuery("""
				update orders.checkout_event_outbox set sent_at = statement_timestamp(), lease_token = null, lease_until = null
				where event_id = :eventId and lease_token = :token
				""").setParameter("eventId", eventId).setParameter("token", token).executeUpdate();
	}

	@Transactional
	public void retry(UUID eventId, UUID token) {
		entities.createNativeQuery("""
				update orders.checkout_event_outbox set next_attempt_at = statement_timestamp() + interval '5 seconds',
					lease_token = null, lease_until = null
				where event_id = :eventId and lease_token = :token
				""").setParameter("eventId", eventId).setParameter("token", token).executeUpdate();
	}
}
