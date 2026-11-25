package com.example.order_service.service;

import com.example.order_service.client.PaymentCheckoutClient;
import com.example.order_service.entity.CheckoutCommand;
import com.example.order_service.repository.CheckoutCommandDeliveryRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class CheckoutCommandRelay {
	private static final Logger log = LoggerFactory.getLogger(CheckoutCommandRelay.class);
	private final CheckoutCommandDeliveryRepository commands;
	private final PaymentCheckoutClient payments;
	private final SqsClient sqs;
	private final JsonMapper json;
	private final Clock clock;
	private final String credential;
	private final String queueUrl;

	public CheckoutCommandRelay(CheckoutCommandDeliveryRepository commands, PaymentCheckoutClient payments,
			SqsClient sqs, JsonMapper json, Clock clock, String credential, String queueUrl) {
		this.commands = commands;
		this.payments = payments;
		this.sqs = sqs;
		this.json = json;
		this.clock = clock;
		this.credential = credential;
		this.queueUrl = queueUrl;
	}

	@Scheduled(fixedDelayString = "${order.checkout.delivery.poll-delay-ms:1000}",
			initialDelayString = "${order.checkout.delivery.poll-delay-ms:1000}")
	public void poll() {
		for (int i = 0; i < 20 && deliverNext(); i++) {
			// Limit the work per poll so other scheduled work can run.
		}
	}

	public boolean deliverNext() {
		var token = UUID.randomUUID();
		var next = commands.claim(token);
		if (next.isEmpty()) {
			return false;
		}
		var command = next.orElseThrow();
		try {
			switch (command.getCommandType()) {
				case "CreateProviderSession" -> createProviderSession(command);
				case "ExpireCheckout" -> sendExpiry(command);
				default -> throw new IllegalStateException("Unexpected checkout command type");
			}
		} catch (FeignException | SdkException | MissingAcknowledgement failure) {
			commands.retry(command.getCommandId(), token);
			log.warn("Checkout command {} type {} will retry after {}", command.getCommandId(), command.getCommandType(),
					failure.getClass().getSimpleName());
			return true;
		}
		commands.acknowledge(command.getCommandId(), token);
		return true;
	}

	private void createProviderSession(CheckoutCommand command) {
		var envelope = payments.create(command.getCheckoutSessionId(), credential);
		if (envelope == null || (envelope.data() != null
				&& !command.getCheckoutSessionId().equals(envelope.data().checkoutSessionId()))) {
			throw new MissingAcknowledgement();
		}
	}

	private void sendExpiry(CheckoutCommand command) {
		var expiresAt = command.getExpiresAt();
		var payload = new ExpireCheckout(command.getCommandId(), command.getCheckoutSessionId(), expiresAt);
		var acknowledgement = sqs.sendMessage(request -> request.queueUrl(queueUrl)
				.messageBody(json.writeValueAsString(payload)).delaySeconds(delayUntil(expiresAt)));
		if (acknowledgement.messageId() == null || acknowledgement.messageId().isBlank()) {
			throw new MissingAcknowledgement();
		}
	}

	private int delayUntil(Instant expiresAt) {
		var remaining = Duration.between(clock.instant(), expiresAt);
		long roundedUp = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
		return (int) Math.clamp(roundedUp, 0, 900);
	}

	private record ExpireCheckout(UUID commandId, UUID checkoutSessionId, Instant expiresAt) {}

	private static final class MissingAcknowledgement extends RuntimeException {}
}
