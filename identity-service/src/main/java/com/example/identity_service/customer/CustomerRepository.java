package com.example.identity_service.customer;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

	/** Serializes address-book changes of one customer, so the address limit holds under concurrent requests. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Customer c where c.customerId = :customerId")
	Optional<Customer> findLockedById(UUID customerId);

	/**
	 * Admin list: optional status, and {@code pattern} ({@code like}, {@code \} escape, already lower-case) on the
	 * user's name, email or phone.
	 */
	@Query("""
			select c from Customer c join c.user u
			where (:status is null or c.status = :status)
			  and (lower(u.fullName) like :pattern escape '\\' or u.email like :pattern escape '\\'
			       or u.phoneNumber like :pattern escape '\\')""")
	Page<Customer> findForAdmin(CustomerStatus status, String pattern, Pageable pageable);

}
