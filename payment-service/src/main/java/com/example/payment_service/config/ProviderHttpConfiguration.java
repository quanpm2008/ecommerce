package com.example.payment_service.config;

import java.net.URI;
import java.time.Duration;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.payment_service.client.PaymentGateway;
import com.example.payment_service.client.ProviderHttpGateway;

import feign.Request;

@Configuration(proxyBeanMethods = false)
public class ProviderHttpConfiguration {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock paymentClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty("payment.provider.base-url")
    PaymentGateway providerHttpGateway(
            @Value("${payment.provider.base-url}") URI baseUrl,
            @Value("${payment.provider.credential-header:}") String credentialHeader,
            @Value("${payment.provider.credential:}") String credential,
            @Value("${payment.provider.timeout:2s}") Duration timeout) {
        return new ProviderHttpGateway(baseUrl, credentialHeader, credential, timeout);
    }

    @Bean
    Request.Options paymentFeignOptions(@Value("${payment.order.timeout:2s}") Duration timeout) {
        return new Request.Options(timeout, timeout, false);
    }
}
