package com.example.finance_service.messaging;

import com.example.finance_service.repository.SettlementInboxRepository;
import com.example.finance_service.repository.WithdrawalOutboxRepository;
import com.example.finance_service.service.PendingSettlementService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@EnableScheduling
@ConditionalOnProperty("finance.kafka.bootstrap-servers")
public class CheckoutPaidListener {
	private final WithdrawalOutboxRepository withdrawals;
	private static final Logger log = LoggerFactory.getLogger(CheckoutPaidListener.class);
	private final PendingSettlementService settlements;
	private final SettlementInboxRepository inbox;
	private final JsonMapper json;
	private final KafkaMessageListenerContainer<String, String> consumer;
	private final KafkaMessageListenerContainer<String, String> refunds;
	private final KafkaProducer<String, String> publisher;
	private final String financeTopic;

	CheckoutPaidListener(PendingSettlementService settlements, SettlementInboxRepository inbox, JsonMapper json,
			WithdrawalOutboxRepository withdrawals,
			@Value("${finance.kafka.bootstrap-servers}") String bootstrap,
			@Value("${finance.kafka.order-topic:ecommerce.order.events.v1}") String topic,
			@Value("${finance.kafka.payment-topic:ecommerce.payment.events.v1}") String paymentTopic,
			@Value("${finance.kafka.result-topic:ecommerce.finance.events.v1}") String financeTopic,
			@Value("${finance.kafka.group:finance-service}") String group) {
		this.settlements = settlements;
		this.inbox = inbox;
		this.json = json;
		this.withdrawals = withdrawals;
		this.financeTopic = financeTopic;
		this.publisher = new KafkaProducer<>(Map.of("bootstrap.servers", bootstrap, "acks", "all",
				"max.block.ms", 3000, "request.timeout.ms", 3000, "delivery.timeout.ms", 6000),
				new StringSerializer(), new StringSerializer());
		consumer = consumer(bootstrap, topic, group, (record, acknowledgment) -> {
			FinanceEvent.Paid event;
			try {
				event = FinanceEvent.checkoutPaid(json, record.value());
				if (event != null && !event.checkoutId().toString().equals(record.key())) {
					throw new IllegalArgumentException("Checkout key mismatch");
				}
			} catch (RuntimeException malformed) {
				settlements.retainInvalid(diagnosticId(record), "checkout.paid", rawOrEmpty(record));
				log.warn("Ignored invalid checkout event at partition {} offset {}", record.partition(), record.offset());
				acknowledgment.acknowledge();
				return;
			}
			if (event != null) {
				settlements.checkoutPaid(event, record.value());
			} else {
				receiveLifecycle(record, false);
			}
			acknowledgment.acknowledge();
		});
		refunds = consumer(bootstrap, paymentTopic, group, (record, acknowledgment) -> {
			receiveLifecycle(record, true);
			acknowledgment.acknowledge();
		});
	}

	private void receiveLifecycle(ConsumerRecord<String, String> record, boolean paymentTopic) {
		String type;
		try {
			var root = json.readTree(rawOrEmpty(record));
			type = root == null ? "" : root.path("eventType").asString("");
		} catch (RuntimeException malformed) {
			if (paymentTopic) {
				settlements.retainInvalid(diagnosticId(record), "refund.succeeded", rawOrEmpty(record));
			}
			return;
		}
		if (paymentTopic ? !"refund.succeeded".equals(type)
				: !("order.cancelled".equals(type) || "order.confirmed".equals(type))) {
			return;
		}
		FinanceEvent.Lifecycle event;
		try {
			event = FinanceEvent.lifecycle(json, record.value());
			if (event != null && !event.aggregateId().toString().equals(record.key())) {
				throw new IllegalArgumentException("Event key mismatch");
			}
		} catch (RuntimeException malformed) {
			settlements.retainInvalid(diagnosticId(record), type, rawOrEmpty(record));
			log.warn("Ignored invalid {} event at partition {} offset {}", type, record.partition(), record.offset());
			return;
		}
		if (event != null) {
			settlements.lifecycle(event, record.value());
		}
	}

	static UUID diagnosticId(ConsumerRecord<String, String> record) {
		String coordinate = record.topic() + ":" + record.partition() + ":" + record.offset();
		return UUID.nameUUIDFromBytes(coordinate.getBytes(StandardCharsets.UTF_8));
	}

	static String rawOrEmpty(ConsumerRecord<String, String> record) {
		return record.value() == null ? "" : record.value();
	}

	@PostConstruct
	void start() {
		consumer.start();
		refunds.start();
	}

	@PreDestroy
	void stop() {
		consumer.stop();
		refunds.stop();
		publisher.close(Duration.ofSeconds(2));
	}

	@Scheduled(fixedDelayString = "${finance.settlement.sweep-delay-ms:1000}")
	void resumeWaiting() {
		for (String raw : inbox.readyCheckouts()) {
			var event = FinanceEvent.checkoutPaid(json, raw);
			try {
				settlements.checkoutPaid(event, raw);
			} catch (RuntimeException failure) {
				log.warn("Could not apply settlement inbox event {}", event.eventId());
			}
		}
		for (String raw : inbox.readyLifecycle()) {
			var event = FinanceEvent.lifecycle(json, raw);
			try {
				settlements.lifecycle(event, raw);
			} catch (RuntimeException failure) {
				log.warn("Could not apply settlement inbox event {}", event.eventId(), failure);
			}
		}
	}

	@Scheduled(fixedDelayString = "${finance.settlement.sweep-delay-ms:1000}")
	void publishFundsAvailable() {
		for (var event : inbox.pendingFundsAvailable()) {
			try {
				var payload = Map.of("orderId", event.orderId(), "sellerId", event.sellerId(),
						"sellerAmountVnd", event.sellerAmountVnd());
				var wire = Map.of("eventId", event.eventId(), "eventType", "funds.available", "version", 1,
						"producer", "finance-service", "aggregateId", event.orderId(), "occurredAt", event.occurredAt(),
						"actor", Map.of("kind", "SERVICE"), "payload", payload);
				publisher.send(new ProducerRecord<>(financeTopic, event.orderId().toString(), json.writeValueAsString(wire)))
						.get(7, TimeUnit.SECONDS);
				settlements.fundsAvailablePublished(event.eventId());
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception failure) {
				log.warn("Could not publish funds.available {}", event.eventId(), failure);
				return;
			}
		}
	}

	@Scheduled(fixedDelayString = "${finance.settlement.sweep-delay-ms:1000}")
	void publishWithdrawals() {
		for (var event : withdrawals.pending()) {
			try {
				var payload = Map.of("withdrawalId", event.withdrawalId(), "sellerId", event.sellerId(),
						"amountVnd", event.amountVnd(), "availableAfterVnd", event.availableAfterVnd());
				var wire = Map.of("eventId", event.eventId(), "eventType", "withdrawal.completed", "version", 1,
						"producer", "finance-service", "aggregateId", event.withdrawalId(), "occurredAt", event.occurredAt(),
						"actor", Map.of("kind", "USER", "userId", event.sellerId(), "context", "SELLER"), "payload", payload);
				publisher.send(new ProducerRecord<>(financeTopic, event.withdrawalId().toString(), json.writeValueAsString(wire)))
						.get(7, TimeUnit.SECONDS);
				withdrawals.markPublished(event.eventId());
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception failure) {
				log.warn("Could not publish withdrawal.completed {}", event.eventId(), failure);
				return;
			}
		}
	}

	static KafkaMessageListenerContainer<String, String> consumer(String bootstrap, String topic, String group,
			AcknowledgingMessageListener<String, String> listener) {
		var factory = new DefaultKafkaConsumerFactory<String, String>(Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
				ConsumerConfig.GROUP_ID_CONFIG, group,
				ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
		var properties = new ContainerProperties(topic);
		properties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
		properties.setMessageListener(listener);
		var consumer = new KafkaMessageListenerContainer<>(factory, properties);
		consumer.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000, FixedBackOff.UNLIMITED_ATTEMPTS)));
		return consumer;
	}
}
