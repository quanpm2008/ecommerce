package com.example.cart_service.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka entry point for {@code checkout.paid}. Off unless {@code cart.events.kafka.enabled=true}, since the compose
 * stack has no broker yet. The offset is committed after {@link CheckoutPaidHandler} commits (ack mode RECORD), so a
 * crash in between redelivers the event and the inbox absorbs it. Database errors are retried with a fixed backoff.
 */
@Configuration
@ConditionalOnProperty(name = "cart.events.kafka.enabled", havingValue = "true")
class CheckoutPaidListener {

	private final CheckoutPaidHandler handler;

	CheckoutPaidListener(CheckoutPaidHandler handler) {
		this.handler = handler;
	}

	@KafkaListener(topics = "${cart.events.checkout-paid-topic}")
	void onCheckoutPaid(String message) {
		handler.handle(message);
	}

	@Bean
	CommonErrorHandler cartKafkaErrorHandler() {
		return new DefaultErrorHandler(new FixedBackOff(5_000L, 60L));
	}

}
