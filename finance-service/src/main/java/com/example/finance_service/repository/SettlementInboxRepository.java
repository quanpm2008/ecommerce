package com.example.finance_service.repository;

import com.example.finance_service.entity.Settlement;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class SettlementInboxRepository {
	private final EntityManager entities;

	public SettlementInboxRepository(EntityManager entities) {
		this.entities = entities;
	}

	public void receive(UUID id, String type, String payload) {
		entities.createNativeQuery("""
				insert into finance.settlement_inbox (event_id, event_type, payload, status)
				values (:id, :type, cast(:payload as jsonb), 'WAITING_DEPENDENCY') on conflict (event_id) do nothing
				""").setParameter("id", id).setParameter("type", type).setParameter("payload", payload).executeUpdate();
	}

	public boolean isWaiting(UUID id, String type, String payload) {
		return !entities.createNativeQuery("""
				select event_id from finance.settlement_inbox
				where event_id = :id and event_type = :type and payload = cast(:payload as jsonb)
				    and status = 'WAITING_DEPENDENCY' for update
				""").setParameter("id", id).setParameter("type", type).setParameter("payload", payload)
				.getResultList().isEmpty();
	}

	public void finish(UUID id, String status) {
		entities.createNativeQuery("update finance.settlement_inbox set status = :status where event_id = :id")
				.setParameter("id", id).setParameter("status", status).executeUpdate();
	}

	public void retainInvalid(UUID id, String type, String raw) {
		entities.createNativeQuery("""
				insert into finance.settlement_inbox (event_id, event_type, payload, status)
				values (:id, :type, to_jsonb(cast(:raw as text)), 'FAILED_REVIEW') on conflict (event_id) do nothing
				""").setParameter("id", id).setParameter("type", type).setParameter("raw", raw).executeUpdate();
	}

	public List<String> readyCheckouts() {
		List<?> rows = entities.createNativeQuery("""
				select cast(i.payload as text) from finance.settlement_inbox i
				where i.status = 'WAITING_DEPENDENCY' and i.event_type = 'checkout.paid'
				    and not exists (
				        select 1 from jsonb_array_elements(i.payload #> '{payload,orders}') as o
				        where not exists (
				            select 1 from finance.wallet w where w.owner_type = 'SELLER' and w.currency = 'VND'
				                and w.owner_id = cast(o->>'sellerId' as uuid)
				        )
				    )
				order by i.received_at, i.event_id limit 20
				""", String.class).getResultList();
		return rows.stream().map(String.class::cast).toList();
	}

	public List<String> readyLifecycle() {
		List<?> rows = entities.createNativeQuery("""
				select cast(i.payload as text) from finance.settlement_inbox i
				join finance.settlement s on cast(i.payload #>> '{payload,orderId}' as uuid) = s.order_id
				where i.status = 'WAITING_DEPENDENCY'
				    and i.event_type in ('order.confirmed', 'order.cancelled', 'refund.succeeded')
				    and (i.event_type <> 'refund.succeeded' or s.status <> 'PENDING')
				    and (i.event_type = 'order.cancelled' or exists (
				        select 1 from finance.wallet w where w.owner_type = 'SELLER' and w.currency = 'VND'
				            and w.owner_id = s.seller_id
				    ))
				order by i.received_at, i.event_id limit 20
				""", String.class).getResultList();
		return rows.stream().map(String.class::cast).toList();
	}

	public void enqueueFundsAvailable(Settlement settlement, Instant now) {
		entities.createNativeQuery("""
				insert into finance.funds_available_outbox (event_id, order_id, seller_id, seller_amount_vnd, occurred_at)
				values (:event, :orderId, :seller, :amount, :at)
				""").setParameter("event", UUID.randomUUID()).setParameter("orderId", settlement.getOrderId())
				.setParameter("seller", settlement.getSellerId()).setParameter("amount", settlement.getSellerAmountVnd())
				.setParameter("at", now).executeUpdate();
	}

	public List<FundsAvailable> pendingFundsAvailable() {
		List<?> rows = entities.createNativeQuery("""
				select event_id, order_id, seller_id, seller_amount_vnd,
				    to_char(occurred_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
				from finance.funds_available_outbox where published_at is null
				order by occurred_at, event_id limit 20
				""").getResultList();
		return rows.stream().map(Object[].class::cast).map(row -> new FundsAvailable(
				(UUID) row[0], (UUID) row[1], (UUID) row[2], ((Number) row[3]).longValue(),
				(String) row[4])).toList();
	}

	public void markFundsAvailablePublished(UUID eventId) {
		entities.createNativeQuery("""
				update finance.funds_available_outbox set published_at = current_timestamp
				where event_id = :id and published_at is null
				""").setParameter("id", eventId).executeUpdate();
	}

	public record FundsAvailable(UUID eventId, UUID orderId, UUID sellerId, long sellerAmountVnd, String occurredAt) {
	}
}
