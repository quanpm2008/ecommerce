package com.example.finance_service.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

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
}
