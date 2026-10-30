package com.example.identity_service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the system clock with a {@link MutableClock}. Tests sharing the context must {@code reset()} it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

	@Bean
	@Primary
	MutableClock mutableClock() {
		return new MutableClock();
	}

}
