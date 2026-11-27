package com.example.identity_service.messaging;

import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "identity.kafka.enabled", havingValue = "true")
class KafkaEventPublisher implements EventPublisher {
	private final KafkaProducer<String, String> producer;
	private final JsonMapper json;
	private final String topic;

	KafkaEventPublisher(JsonMapper json,
			@Value("${identity.kafka.bootstrap-servers}") String bootstrap,
			@Value("${identity.kafka.topic:ecommerce.identity.events.v1}") String topic) {
		this.json = json;
		this.topic = topic;
		this.producer = new KafkaProducer<>(Map.of(
				"bootstrap.servers", bootstrap, "acks", "all",
				"enable.idempotence", true, "max.block.ms", 2000,
				"request.timeout.ms", 2000, "delivery.timeout.ms", 4000,
				"linger.ms", 0,
				"key.serializer", StringSerializer.class,
				"value.serializer", StringSerializer.class));
	}

	@Override
	public boolean isConfigured() {
		return true;
	}

	@Override
	public void publish(String eventType, Map<String, Object> envelope) {
		try {
			String key = envelope.get("aggregateId").toString();
			producer.send(new ProducerRecord<>(topic, key, json.writeValueAsString(envelope))).get(4, TimeUnit.SECONDS);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Kafka publication interrupted", interrupted);
		} catch (ExecutionException | TimeoutException failure) {
			throw new IllegalStateException("Kafka did not acknowledge the event", failure);
		}
	}

	@PreDestroy
	void close() {
		producer.close(Duration.ofSeconds(2));
	}
}
