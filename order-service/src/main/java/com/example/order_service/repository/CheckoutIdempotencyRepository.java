package com.example.order_service.repository;

import com.example.order_service.entity.CheckoutIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface CheckoutIdempotencyRepository extends JpaRepository<CheckoutIdempotency, CheckoutIdempotency.Key> {
	@Modifying
	@Query(value = """
			insert into orders.idempotency_record
				(actor_user_id, operation_context, endpoint, idempotency_key, request_hash, result_ref)
			values (:customer, 'CUSTOMER', :endpoint, :key, :hash, :session) on conflict do nothing
			""", nativeQuery = true)
	int insertAlias(UUID customer, String endpoint, String key, String hash, UUID session);
}
