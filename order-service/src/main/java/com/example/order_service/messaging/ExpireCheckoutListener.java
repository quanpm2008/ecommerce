package com.example.order_service.messaging;

import com.example.order_service.service.CheckoutOutcomeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "order.checkout.delivery.enabled", havingValue = "true")
public class ExpireCheckoutListener {
	private static final Logger log = LoggerFactory.getLogger(ExpireCheckoutListener.class);
	private final SqsClient sqs;
	private final CheckoutOutcomeService outcomes;
	private final JsonMapper json;
	private final String queue;

	ExpireCheckoutListener(SqsClient checkoutSqsClient, CheckoutOutcomeService outcomes, JsonMapper json,
			@Value("${order.checkout.delivery.queue-url}") String queue) {
		this.sqs = checkoutSqsClient;
		this.outcomes = outcomes;
		this.json = json;
		this.queue = queue;
	}

	@Scheduled(fixedDelayString = "${order.checkout.delivery.poll-delay-ms:1000}",
			initialDelayString = "${order.checkout.delivery.poll-delay-ms:1000}")
	public void receiveOnce() {
		var messages = sqs.receiveMessage(request -> request.queueUrl(queue).maxNumberOfMessages(10).waitTimeSeconds(1));
		for (var message : messages.messages()) {
			try {
				receive(message);
			} catch (RuntimeException retry) {
				log.warn("ExpireCheckout SQS message {} retained for retry ({})", message.messageId(),
						retry.getClass().getSimpleName());
			}
		}
	}

	private void receive(Message message) {
		var payload = json.readTree(message.body());
		UUID commandId = UUID.fromString(payload.path("commandId").asString());
		UUID checkoutId = UUID.fromString(payload.path("checkoutSessionId").asString());
		Instant expiresAt = Instant.parse(payload.path("expiresAt").asString());
		var result = outcomes.expire(commandId, checkoutId, expiresAt);
		// The transactional proxy returns only after the expiry and its outbox row have committed.
		switch (result.disposition()) {
			case ACKNOWLEDGE -> sqs.deleteMessage(request -> request.queueUrl(queue).receiptHandle(message.receiptHandle()));
			case EARLY -> sqs.changeMessageVisibility(request -> request.queueUrl(queue)
					.receiptHandle(message.receiptHandle()).visibilityTimeout(result.visibilitySeconds()));
			case INVALID -> log.warn("ExpireCheckout SQS message {} does not match the stored command", message.messageId());
		}
	}
}
