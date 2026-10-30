package com.example.identity_service.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

	long countByCustomerIdAndDeletedAtIsNull(UUID customerId);

}
