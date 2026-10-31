package com.example.identity_service.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

	long countByCustomerIdAndDeletedAtIsNull(UUID customerId);

	/**
	 * The address book in display order (API §6.1, DB §3.1.4): latest {@code lastUsedAt} first, never-used addresses
	 * after in creation order. The first entry is therefore the default address.
	 */
	@Query("""
			select a from CustomerAddress a where a.customerId = :customerId and a.deletedAt is null
			order by a.lastUsedAt desc nulls last, a.createdAt asc, a.addressId asc""")
	List<CustomerAddress> findBook(UUID customerId);

	Optional<CustomerAddress> findByAddressIdAndCustomerIdAndDeletedAtIsNull(UUID addressId, UUID customerId);

	/** Records a checkout's use of an address once; 0 means this checkout session already touched it. */
	@Modifying
	@Query(value = """
			insert into address_touches (address_id, checkout_session_id) values (:addressId, :checkoutSessionId)
			on conflict do nothing""", nativeQuery = true)
	int recordTouch(UUID addressId, String checkoutSessionId);

}
