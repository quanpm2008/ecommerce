package com.example.finance_service.repository;

import com.example.finance_service.entity.Wallet;
import com.example.finance_service.entity.Wallet.OwnerType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID>, JpaSpecificationExecutor<Wallet> {
	Optional<Wallet> findByOwnerTypeAndOwnerIdAndCurrency(OwnerType ownerType, UUID ownerId, String currency);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Wallet> findByOwnerTypeAndCurrency(OwnerType ownerType, String currency);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<Wallet> findByOwnerTypeAndCurrencyAndOwnerIdInOrderByOwnerId(
			OwnerType ownerType, String currency, Collection<UUID> ownerIds);

	@Modifying
	@Query(value = """
			insert into finance.wallet
			    (wallet_id, owner_type, owner_id, currency, pending_balance_vnd, available_balance_vnd,
			     total_withdrawn_vnd, row_version, created_at, updated_at)
			values (:walletId, 'SELLER', :sellerId, 'VND', 0, 0, 0, 0, :at, :at)
			on conflict (owner_id, currency) where owner_type = 'SELLER' do nothing
			""", nativeQuery = true)
	void provisionApprovedSeller(UUID walletId, UUID sellerId, Instant at);
}
