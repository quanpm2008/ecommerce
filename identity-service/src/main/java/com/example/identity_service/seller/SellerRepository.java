package com.example.identity_service.seller;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface SellerRepository extends JpaRepository<Seller, UUID> {

	/** Same normalization and scope as the {@code uq_sellers_seller_name_active} index. */
	@Query(value = """
			select exists (select 1 from sellers
			where lower(regexp_replace(btrim(seller_name), '\\s+', ' ', 'g'))
			      = lower(regexp_replace(btrim(:sellerName), '\\s+', ' ', 'g'))
			  and approval_status in ('PENDING', 'APPROVED'))""", nativeQuery = true)
	boolean existsActiveSellerName(String sellerName);

	/** {@code contactPhone} must already be normalized; same scope as {@code uq_sellers_contact_phone_active}. */
	@Query(value = """
			select exists (select 1 from sellers
			where contact_phone = :contactPhone and approval_status in ('PENDING', 'APPROVED'))""", nativeQuery = true)
	boolean existsActiveContactPhone(String contactPhone);

}
