package com.example.order_service.repository;

import com.example.order_service.entity.CheckoutSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CheckoutSessionJpaRepository extends JpaRepository<CheckoutSession, UUID> {
	Page<CheckoutSession> findByNeedsManualReviewTrue(Pageable pageable);

	@Query(value = """
			select count(*) as "paidCount", coalesce(sum(total_amount_vnd),0) as "paidTotal",
			       statement_timestamp() as "readAt" from orders.checkout_session where status='PAID'
			""", nativeQuery = true)
	PaidTotals paidTotals();

	interface PaidTotals {
		long getPaidCount();
		long getPaidTotal();
		Instant getReadAt();
	}
	Optional<CheckoutSession> findByCustomerIdAndStatus(UUID customerId, CheckoutSession.Status status);

	Optional<CheckoutSession> findByCheckoutSessionIdAndCustomerId(UUID checkoutSessionId, UUID customerId);

	boolean existsByPaymentIdAndCheckoutSessionIdNot(UUID paymentId, UUID checkoutSessionId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CheckoutSession c where c.checkoutSessionId = :checkoutId")
	Optional<CheckoutSession> lock(UUID checkoutId);

	@Query(value = """
			select exists(select 1 from orders.checkout_session where checkout_session_id = :checkoutId
				and status = 'AWAITING_PAYMENT' and expires_at > statement_timestamp()
				and :paymentExpiresAt > statement_timestamp())
			""", nativeQuery = true)
	boolean canExposeLink(UUID checkoutId, Instant paymentExpiresAt);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select c from CheckoutSession c where c.customerId = :customer
				and (c.checkoutSessionId = :keySession or c.checkoutIntentId = :intent)
			order by c.checkoutSessionId
			""")
	List<CheckoutSession> lockForReplay(UUID customer, UUID keySession, UUID intent);
}
