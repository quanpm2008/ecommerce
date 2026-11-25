package com.example.finance_service;

import com.example.finance_service.entity.Settlement;
import com.example.finance_service.entity.Wallet;
import com.example.finance_service.entity.WalletTransaction;
import com.example.finance_service.repository.SettlementRepository;
import com.example.finance_service.repository.WalletRepository;
import com.example.finance_service.repository.WalletTransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class FinanceServiceApplicationTests {
	@Autowired
	private WalletRepository wallets;

	@Autowired
	private SettlementRepository settlements;

	@Autowired
	private WalletTransactionRepository ledger;

	@Autowired
	private EntityManager entities;

	@Test
	void validatesTheSchemaAndLoadsTheSeededPlatformWallet() {
		assertThat(wallets.findAll()).anySatisfy(wallet -> {
			assertThat(wallet.getOwnerType()).isEqualTo(Wallet.OwnerType.PLATFORM);
			assertThat(wallet.getCurrency()).isEqualTo("VND");
			assertThat(wallet.getOwnerId()).isNull();
		});
	}

	@Test
	void persistsAndReloadsSettlementAndLedgerMappings() {
		var now = Instant.now();
		var seller = UUID.randomUUID();
		var payment = UUID.randomUUID();
		var wallet = wallets.saveAndFlush(new Wallet(UUID.randomUUID(), Wallet.OwnerType.SELLER, seller, now, now));
		var settlement = settlements.saveAndFlush(new Settlement(UUID.randomUUID(), UUID.randomUUID(), payment, seller,
				1000, 500, 50, 950, Settlement.Status.PENDING, now, null));
		var entry = ledger.saveAndFlush(new WalletTransaction(UUID.randomUUID(), wallet.getWalletId(),
				settlement.getSettlementId(), null, WalletTransaction.ReferenceType.PAYMENT, payment,
				WalletTransaction.TransactionType.PAYMENT_RECEIVED, 950, 0, 0,
				"settlement:" + settlement.getOrderId() + ":" + wallet.getWalletId(), now));
		entities.clear();
		assertThat(ledger.findById(entry.getWalletTransactionId()).orElseThrow().getPendingDeltaVnd()).isEqualTo(950L);
		assertThat(settlements.findById(settlement.getSettlementId()).orElseThrow().getSellerAmountVnd()).isEqualTo(950L);
	}
}
