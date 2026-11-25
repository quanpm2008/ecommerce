package com.example.cart_service.client;

import com.example.cart_service.common.web.RequestIdFilter;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Configuration of Cart's internal Feign clients (not a global {@code @Configuration}): every call carries Cart's
 * {@code X-Service-Token} and the current {@code X-Request-Id}. The user's bearer token is sent only where an endpoint
 * asks for it explicitly (Identity's authorization context).
 */
public class InternalFeignConfig {

	public static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

	@Bean
	RequestInterceptor cartInternalHeaders(@Value("${cart.internal.service-token:}") String identityToken,
			@Value("${cart.product-token:}") String productToken, @Value("${cart.order-token:}") String orderToken) {
		return template -> {
			String serviceToken = switch (template.feignTarget().name()) {
				case "identity-service" -> identityToken;
				case "product-service" -> productToken;
				case "order-service" -> orderToken;
				default -> "";
			};
			if (!serviceToken.isBlank()) {
				template.header(SERVICE_TOKEN_HEADER, serviceToken);
			}
			String requestId = MDC.get(RequestIdFilter.MDC_KEY);
			if (requestId != null) {
				template.header(RequestIdFilter.HEADER, requestId);
			}
		};
	}

}
