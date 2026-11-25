package com.example.order_service.messaging;

import com.example.order_service.service.CheckoutOutcomeService;
import com.example.order_service.service.CheckoutOutcomeService.PaymentOutcome;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty("order.kafka.bootstrap-servers")
public class PaymentOutcomeListener {
	private static final Logger log = LoggerFactory.getLogger(PaymentOutcomeListener.class);
	private final CheckoutOutcomeService outcomes;
	private final JsonMapper json;
	private final KafkaMessageListenerContainer<String, String> listener;

	PaymentOutcomeListener(CheckoutOutcomeService outcomes, JsonMapper json,
			@Value("${order.kafka.bootstrap-servers}") String bootstrap,
			@Value("${order.kafka.payment-topic:ecommerce.payment.events.v1}") String paymentTopic,
			@Value("${order.kafka.payment-group:order-service-payment}") String group) {
		this.outcomes = outcomes;
		this.json = json;
		var factory = new DefaultKafkaConsumerFactory<String, String>(Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
				ConsumerConfig.GROUP_ID_CONFIG, group,
				ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
		var properties = new ContainerProperties(paymentTopic);
		properties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
		properties.setMessageListener((AcknowledgingMessageListener<String, String>) this::receive);
		listener = new KafkaMessageListenerContainer<>(factory, properties);
		listener.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000, FixedBackOff.UNLIMITED_ATTEMPTS)));
	}

	@PostConstruct
	void start() {
		listener.start();
	}

	@PreDestroy
	void stop() {
		listener.stop();
	}

	private void receive(ConsumerRecord<String, String> record, Acknowledgment ack) {
		PaymentOutcome outcome;
		try {
			outcome = parse(record);
		} catch (RuntimeException malformed) {
			String key = record.topic() + ':' + record.partition() + ':' + record.offset();
			var diagnosticId = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
			outcomes.invalidPayment(diagnosticId, record.value() == null ? "" : record.value());
			log.warn("Payment event retained for review at partition {} offset {}", record.partition(), record.offset());
			ack.acknowledge();
			return;
		}
		if (outcome != null) {
			outcomes.payment(outcome, record.value());
		}
		ack.acknowledge();
	}

	private PaymentOutcome parse(ConsumerRecord<String, String> record) {
		var envelope = json.readTree(record.value());
		String type = envelope.path("eventType").asString(null);
		if (type == null || type.isBlank()) {
			throw new IllegalArgumentException("Missing event type");
		}
		if (!"payment.succeeded".equals(type) && !"payment.failed".equals(type)) {
			return null;
		}
		var payload = envelope.path("payload");
		var version = envelope.path("version");
		var actor = envelope.path("actor");
		UUID paymentId = UUID.fromString(payload.path("paymentId").asString());
		if (!"payment-service".equals(envelope.path("producer").asString())
				|| !paymentId.toString().equals(envelope.path("aggregateId").asString())
				|| !paymentId.toString().equals(record.key())
				|| !version.isIntegralNumber() || !version.canConvertToLong() || version.asLong() < 1
				|| !"SERVICE".equals(actor.path("kind").asString())) {
			throw new IllegalArgumentException("Invalid payment event envelope");
		}
		Instant.parse(envelope.path("occurredAt").asString());
		Long amount = "payment.succeeded".equals(type) ? readAmount(payload) : null;
		Instant completedAt = amount == null ? null : Instant.parse(payload.path("completedAt").asString());
		if (amount == null && (!payload.path("errorCode").isString() || payload.path("errorCode").asString().isBlank())) {
			throw new IllegalArgumentException("Missing Payment failure code");
		}
		return new PaymentOutcome(UUID.fromString(envelope.path("eventId").asString()),
				UUID.fromString(payload.path("checkoutSessionId").asString()), paymentId, amount, completedAt);
	}

	private static long readAmount(JsonNode payload) {
		var amount = payload.path("amountVnd");
		if (!amount.isIntegralNumber() || !amount.canConvertToLong() || amount.asLong() <= 0) {
			throw new IllegalArgumentException("Invalid Payment amount");
		}
		return amount.asLong();
	}
}
