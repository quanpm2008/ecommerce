package com.example.identity_service;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.order.OrderClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Order's {@code has-paid-order} in place of order-service: customers marked with {@link #markPaid(UUID)} have a
 * paid order; {@link #setAvailable(boolean)} simulates an outage.
 */
public class FakeOrderClient implements OrderClient {

	private final Set<UUID> paid = ConcurrentHashMap.newKeySet();

	private final AtomicInteger calls = new AtomicInteger();

	private volatile boolean available = true;

	public void markPaid(UUID customerId) {
		paid.add(customerId);
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}

	public int calls() {
		return calls.get();
	}

	public void reset() {
		paid.clear();
		calls.set(0);
		available = true;
	}

	@Override
	public boolean hasPaidOrder(UUID customerId) {
		calls.incrementAndGet();
		if (!available) {
			throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
		}
		return paid.contains(customerId);
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Configuration {

		@Bean
		@Primary
		FakeOrderClient fakeOrderClient() {
			return new FakeOrderClient();
		}

	}

}
