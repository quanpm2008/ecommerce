package com.example.finance_service.messaging;

import com.example.finance_service.repository.SettlementInboxRepository;
import com.example.finance_service.service.PendingSettlementService;
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
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Component
@EnableScheduling
@ConditionalOnProperty("finance.kafka.bootstrap-servers")
public class CheckoutPaidListener {
	private static final Logger log = LoggerFactory.getLogger(CheckoutPaidListener.class);
	private final PendingSettlementService settlements;
	private final SettlementInboxRepository inbox;
	private final JsonMapper json;
	private final KafkaMessageListenerContainer<String, String> consumer;

	CheckoutPaidListener(PendingSettlementService settlements, SettlementInboxRepository inbox, JsonMapper json,
			@Value("${finance.kafka.bootstrap-servers}") String bootstrap,
			@Value("${finance.kafka.order-topic:ecommerce.order.events.v1}") String topic,
			@Value("${finance.kafka.group:finance-service}") String group) {
		this.settlements = settlements;
		this.inbox = inbox;
		this.json = json;
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
			}
			acknowledgment.acknowledge();
		});
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
	}

	@PreDestroy
	void stop() {
		consumer.stop();
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
