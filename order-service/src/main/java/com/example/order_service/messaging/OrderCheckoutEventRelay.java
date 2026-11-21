package com.example.order_service.messaging;

import com.example.order_service.repository.CheckoutOutcomeRepository;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@EnableScheduling
@ConditionalOnProperty("order.kafka.bootstrap-servers")
public class OrderCheckoutEventRelay {
	private static final Logger log = LoggerFactory.getLogger(OrderCheckoutEventRelay.class);
	private final CheckoutOutcomeRepository outbox;
	private final KafkaProducer<String, String> producer;
	private final String topic;

	OrderCheckoutEventRelay(CheckoutOutcomeRepository outbox,
			@Value("${order.kafka.bootstrap-servers}") String bootstrap,
			@Value("${order.kafka.checkout-topic:ecommerce.order.events.v1}") String topic) {
		this.outbox = outbox;
		this.topic = topic;
		producer = new KafkaProducer<>(Map.of(
				ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
				ProducerConfig.ACKS_CONFIG, "all",
				ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000,
				ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000,
				ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 5000,
				ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
	}

	@Scheduled(initialDelayString = "${order.kafka.poll-delay-ms:1000}", fixedDelayString = "${order.kafka.poll-delay-ms:1000}")
	public void poll() {
		for (int i = 0; i < 20 && deliverNext(); i++) {
			// Bound work per scheduled poll.
		}
	}

	public boolean deliverNext() {
		UUID token = UUID.randomUUID();
		var next = outbox.claim(token);
		if (next.isEmpty()) {
			return false;
		}
		var event = next.orElseThrow();
		try {
			producer.send(new ProducerRecord<>(topic, event.getCheckoutSessionId().toString(), event.getEnvelope()))
					.get(6, TimeUnit.SECONDS);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return false;
		} catch (ExecutionException | TimeoutException | RuntimeException unavailable) {
			outbox.retry(event.getEventId(), token);
			log.warn("Checkout event {} will retry ({})", event.getEventId(), unavailable.getClass().getSimpleName());
			return false;
		}
		outbox.acknowledge(event.getEventId(), token);
		return true;
	}

	@PreDestroy
	void close() {
		producer.close(Duration.ofSeconds(3));
	}
}
