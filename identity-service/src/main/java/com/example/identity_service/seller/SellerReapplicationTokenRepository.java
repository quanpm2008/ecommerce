package com.example.identity_service.seller;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface SellerReapplicationTokenRepository extends JpaRepository<SellerReapplicationToken, UUID> {

	/** Locked, so the same token presented twice at once is used only once. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<SellerReapplicationToken> findByTokenHash(String tokenHash);

}
