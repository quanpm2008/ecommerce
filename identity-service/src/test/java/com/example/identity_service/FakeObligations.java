package com.example.identity_service;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.obligation.AccountObligations;
import com.example.identity_service.obligation.ObligationSource;
import com.example.identity_service.user.User;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Order's and Finance's obligations in place of the real services: an account has none unless set with
 * {@link #set}; {@link #setAvailable(boolean)} simulates a source that cannot answer.
 */
public class FakeObligations implements ObligationSource {

	public static final AccountObligations NONE = new AccountObligations(false, false, false, false, 0, 0, null);

	private final Map<UUID, AccountObligations> byAccount = new ConcurrentHashMap<>();

	private final AtomicInteger calls = new AtomicInteger();

	private volatile boolean available = true;

	public void set(UUID userId, AccountObligations obligations) {
		byAccount.put(userId, obligations);
	}

	public void openOrders(UUID userId) {
		set(userId, new AccountObligations(true, false, false, false, 0, 0, null));
	}

	public void clear(UUID userId) {
		byAccount.remove(userId);
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}

	public int calls() {
		return calls.get();
	}

	public void reset() {
		byAccount.clear();
		calls.set(0);
		available = true;
	}

	@Override
	public AccountObligations of(User account) {
		calls.incrementAndGet();
		if (!available) {
			throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
		}
		AccountObligations found = byAccount.getOrDefault(account.getUserId(), NONE);
		return new AccountObligations(found.hasOpenOrders(), found.hasPendingPayment(), found.hasUnresolvedRefund(),
				found.hasPendingFinance(), found.pendingBalanceVnd(), found.availableBalanceVnd(), Instant.now());
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Configuration {

		@Bean
		@Primary
		FakeObligations fakeObligations() {
			return new FakeObligations();
		}

	}

}
