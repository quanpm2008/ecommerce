package com.example.order_service.config;

import com.example.order_service.client.PaymentCheckoutClient;
import com.example.order_service.repository.CheckoutCommandDeliveryRepository;
import com.example.order_service.service.CheckoutCommandRelay;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "order.checkout.delivery.enabled", havingValue = "true")
public class CheckoutDeliveryConfiguration {
	@Bean
	SqsClient checkoutSqsClient(@Value("${order.checkout.delivery.region}") String region,
			@Value("${order.checkout.delivery.endpoint:}") String endpoint) {
		var builder = SqsClient.builder().region(Region.of(region))
				.overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(5)));
		if (!endpoint.isBlank()) {
			builder.endpointOverride(URI.create(endpoint));
		}
		return builder.build();
	}

	@Bean
	CheckoutCommandRelay checkoutCommandRelay(CheckoutCommandDeliveryRepository commands,
			PaymentCheckoutClient payments, SqsClient checkoutSqsClient, JsonMapper json, ObjectProvider<Clock> clock,
			@Value("${order.payment.service-token:}") String credential,
			@Value("${order.checkout.delivery.queue-url}") String queueUrl) {
		if (credential.isBlank() || queueUrl.isBlank() || URI.create(queueUrl).getPath().endsWith(".fifo")) {
			throw new IllegalStateException("Set the Payment credential and a Standard SQS queue URL for checkout delivery");
		}
		return new CheckoutCommandRelay(commands, payments, checkoutSqsClient, json,
				clock.getIfAvailable(Clock::systemUTC), credential, queueUrl);
	}
}
