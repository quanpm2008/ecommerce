package com.example.identity_service.seller;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SellerApplicationRepository extends JpaRepository<SellerApplication, UUID> {

	/** The seller's latest submission. */
	Optional<SellerApplication> findFirstBySellerIdOrderBySubmissionNoDesc(UUID sellerId);

	/** Every submission, newest first. */
	List<SellerApplication> findBySellerIdOrderBySubmissionNoDesc(UUID sellerId);

}
