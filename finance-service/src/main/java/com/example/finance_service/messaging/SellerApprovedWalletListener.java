package com.example.finance_service.messaging;

import com.example.finance_service.service.PendingSettlementService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty("finance.kafka.bootstrap-servers")
public class SellerApprovedWalletListener {
	private static final Logger log = LoggerFactory.getLogger(SellerApprovedWalletListener.class);
	private final KafkaMessageListenerContainer<String, String> consumer;

	SellerApprovedWalletListener(PendingSettlementService settlements, JsonMapper json,
			@Value("${finance.kafka.bootstrap-servers}") String bootstrap,
			@Value("${finance.kafka.identity-topic:ecommerce.identity.events.v1}") String topic,
			@Value("${finance.kafka.group:finance-service}") String group) {
		consumer = CheckoutPaidListener.consumer(bootstrap, topic, group, (record, acknowledgment) -> {
			FinanceEvent.Approved event;
			try {
				event = FinanceEvent.sellerApproved(json, record.value());
				if (event != null && !event.sellerId().toString().equals(record.key())) {
					throw new IllegalArgumentException("Seller key mismatch");
				}
			} catch (RuntimeException malformed) {
				settlements.retainInvalid(CheckoutPaidListener.diagnosticId(record), "seller.approved",
						CheckoutPaidListener.rawOrEmpty(record));
				log.warn("Ignored invalid seller event at partition {} offset {}", record.partition(), record.offset());
				acknowledgment.acknowledge();
				return;
			}
			if (event != null) {
				settlements.sellerApproved(event, record.value());
			}
			acknowledgment.acknowledge();
		});
	}

	@PostConstruct
	void start() {
		consumer.start();
	}

	@PreDestroy
	void stop() {
		consumer.stop();
	}
}
