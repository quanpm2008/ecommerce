package com.example.order_service.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CheckoutSaleEvidenceRepository {
	private final EntityManager entities;

	CheckoutSaleEvidenceRepository(EntityManager entities) {
		this.entities = entities;
	}

	public boolean schedule(UUID checkoutId, UUID paymentId, Instant completedAt) {
		entities.createNativeQuery("""
				insert into orders.checkout_sale_evidence (checkout_session_id, payment_id, completed_at)
				values (:checkoutId, :paymentId, :completedAt) on conflict (checkout_session_id) do nothing
				""").setParameter("checkoutId", checkoutId).setParameter("paymentId", paymentId)
				.setParameter("completedAt", completedAt.toString()).executeUpdate();
		var existing = (Object[]) entities.createNativeQuery("""
				select payment_id, completed_at from orders.checkout_sale_evidence where checkout_session_id = :checkoutId
				""").setParameter("checkoutId", checkoutId).getSingleResult();
		return paymentId.equals(existing[0]) && completedAt.equals(Instant.parse((String) existing[1]));
	}

	@Transactional
	public Optional<Work> claim(UUID token) {
		var rows = entities.createNativeQuery("""
				with due as (
					select checkout_session_id from orders.checkout_sale_evidence
					where next_attempt_at <= statement_timestamp()
						and (lease_until is null or lease_until <= statement_timestamp())
					order by next_attempt_at, checkout_session_id limit 1 for update skip locked
				)
				update orders.checkout_sale_evidence w set lease_token = :token,
					lease_until = statement_timestamp() + interval '30 seconds'
				from due where w.checkout_session_id = due.checkout_session_id
				returning w.checkout_session_id, w.payment_id, w.completed_at
				""").setParameter("token", token).getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		var row = (Object[]) rows.getFirst();
		return Optional.of(new Work((UUID) row[0], (UUID) row[1], Instant.parse((String) row[2]), token));
	}

	public boolean lockClaim(Work work) {
		return !entities.createNativeQuery("""
				select 1 from orders.checkout_sale_evidence where checkout_session_id = :checkoutId
					and lease_token = :token and lease_until > statement_timestamp() for update
				""").setParameter("checkoutId", work.checkoutId()).setParameter("token", work.token())
				.getResultList().isEmpty();
	}

	public void postpone(UUID checkoutId, String reason) {
		entities.createNativeQuery("""
				update orders.checkout_sale_evidence set next_attempt_at = statement_timestamp() + interval '5 seconds',
					lease_token = null, lease_until = null, last_error = :reason
				where checkout_session_id = :checkoutId
				""").setParameter("checkoutId", checkoutId).setParameter("reason", reason).executeUpdate();
	}

	public void stop(UUID checkoutId, String reason) {
		entities.createNativeQuery("""
				update orders.checkout_sale_evidence set next_attempt_at = null, lease_token = null, lease_until = null,
					last_error = coalesce(:reason, last_error) where checkout_session_id = :checkoutId
				""").setParameter("checkoutId", checkoutId).setParameter("reason", reason).executeUpdate();
	}

	public record Work(UUID checkoutId, UUID paymentId, Instant completedAt, UUID token) {}
}
