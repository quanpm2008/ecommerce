package com.example.finance_service.repository;

import com.example.finance_service.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID>,
		JpaSpecificationExecutor<WalletTransaction> {
	@Query("""
			select coalesce(sum(t.pendingDeltaVnd),0) as pending,
			       coalesce(sum(t.availableDeltaVnd),0) as available,
			       coalesce(sum(t.withdrawnDeltaVnd),0) as withdrawn
			from WalletTransaction t where t.walletId = :walletId
			""")
	Balances totals(UUID walletId);

	interface Balances {
		long getPending();
		long getAvailable();
		long getWithdrawn();
	}
	boolean existsBySettlementIdAndTransactionTypeAndReferenceId(UUID settlementId, WalletTransaction.TransactionType type,
			UUID referenceId);
}
