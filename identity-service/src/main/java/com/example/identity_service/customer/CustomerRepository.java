package com.example.identity_service.customer;

import jakarta.persistence.LockModeType;
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

}
