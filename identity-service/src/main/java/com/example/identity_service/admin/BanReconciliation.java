package com.example.identity_service.admin;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.UUID;

/**
 * J7 (API §7.3): every {@code identity.ban-reconciliation.interval}, each banned account (BANNED or DISABLED) is
 * reconciled with its current obligations ({@link ModerationService#reconcile}). A source that cannot answer leaves
 * the account as it is until the next run; nothing is guessed, nothing becomes ACTIVE. Off with
 * {@code identity.ban-reconciliation.enabled=false} (tests run it themselves).
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "identity.ban-reconciliation.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
class BanReconciliation {

	private final UserRepository users;

	private final ModerationService moderation;

	@Scheduled(fixedDelayString = "${identity.ban-reconciliation.interval:PT10M}",
			initialDelayString = "${identity.ban-reconciliation.interval:PT10M}")
	void run() {
		for (UUID userId : users.findIdsOfBannedAccounts()) {
			try {
				moderation.reconcile(userId);
			}
			catch (ApiException unavailable) {
				log.info("J7: account {} kept as it is, obligations unavailable", userId);
			}
		}
	}

}
