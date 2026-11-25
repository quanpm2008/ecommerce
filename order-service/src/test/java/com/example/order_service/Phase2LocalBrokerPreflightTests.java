package com.example.order_service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf("preflightRequested")
class Phase2LocalBrokerPreflightTests {
	static boolean preflightRequested() {
		return System.getenv("PHASE2_4A_PROVIDER_URL") != null
				|| System.getProperty("test", "").contains("Phase2LocalBrokerPreflightTests");
	}

	@Test
	void exchangesMessagesWithIsolatedPostgresKafkaElasticMqAndProvider() throws Exception {
		try (var infrastructure = new Phase2LocalBrokerSupport()) {
			infrastructure.infrastructure();
			try (var observer = infrastructure.observer(infrastructure.identityTopic)) {
				String marker = UUID.randomUUID().toString();
				infrastructure.send(infrastructure.identityTopic, marker, marker);
				Phase2LocalBrokerSupport.waitFor("Kafka probe", 10, () -> {
					for (var record : observer.poll(Duration.ofMillis(500))) {
						if (marker.equals(record.key()) && marker.equals(record.value())) return true;
					}
					return false;
				});
				infrastructure.sqs.sendMessage(r -> r.queueUrl(infrastructure.queue).messageBody(marker));
				var message = Phase2LocalBrokerSupport.waitFor("ElasticMQ probe", 8,
						() -> infrastructure.sqs.receiveMessage(r -> r.queueUrl(infrastructure.queue)
								.waitTimeSeconds(1)).messages().stream().findFirst().orElse(null));
				assertThat(message.body()).isEqualTo(marker);
				infrastructure.sqs.deleteMessage(r -> r.queueUrl(infrastructure.queue).receiptHandle(message.receiptHandle()));
			}
		}
	}
}
