package com.example.cart_service.cart.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

	List<CartItem> findByCartIdOrderByCreatedAtAscCartItemIdAsc(UUID cartId);

	Optional<CartItem> findByCartItemIdAndCartId(UUID cartItemId, UUID cartId);

	Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);

	long countByCartId(UUID cartId);

	@Query("select coalesce(sum(i.quantity), 0) from CartItem i where i.cartId = :cartId")
	long sumQuantityByCartId(@Param("cartId") UUID cartId);

}
