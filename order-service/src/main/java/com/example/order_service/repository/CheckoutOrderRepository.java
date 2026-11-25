package com.example.order_service.repository;

import com.example.order_service.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckoutOrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
	List<Order> findByCheckoutSessionIdOrderBySellerId(UUID checkoutSessionId);
	boolean existsBySellerIdAndStatusIn(UUID sellerId, List<Order.Status> statuses);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Order o where o.orderId = :id")
	Optional<Order> lock(UUID id);

	@Query("""
			select o.orderId from Order o where o.status in :states and o.sellerActionDueAt <= :now
			order by o.sellerActionDueAt, o.orderId
			""")
	List<UUID> sellerOverdue(List<Order.Status> states, Instant now, Pageable page);

	@Query("""
			select o.orderId from Order o where o.status = :state and o.customerConfirmDueAt <= :now
			order by o.customerConfirmDueAt, o.orderId
			""")
	List<UUID> customerOverdue(Order.Status state, Instant now, Pageable page);
}
