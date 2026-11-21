package com.example.cart_service.cart.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

	/** Creates the customer's cart unless one exists; safe under concurrent first requests (UNIQUE customer_id). */
	@Modifying
	@Query(value = """
			INSERT INTO carts (cart_id, customer_id, row_version, created_at, updated_at)
			VALUES (:cartId, :customerId, 0, now(), now())
			ON CONFLICT (customer_id) DO NOTHING
			""", nativeQuery = true)
	int insertIfAbsent(@Param("cartId") UUID cartId, @Param("customerId") UUID customerId);

	Optional<Cart> findByCustomerId(UUID customerId);

	/** Serializes every change to one cart, so the 50-line and 99-unit limits hold under concurrent requests. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Cart c where c.customerId = :customerId")
	Optional<Cart> lockByCustomerId(@Param("customerId") UUID customerId);

}
