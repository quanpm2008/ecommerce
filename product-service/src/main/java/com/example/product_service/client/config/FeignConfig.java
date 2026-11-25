package com.example.product_service.client.config;

import feign.Request;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignConfig {

    @Value("${internal.security.service-token:}")
    private String identityToken;

    @Value("${product.order.service-token:}")
    private String orderToken;

    @Value("${product.notification.service-token:}")
    private String notificationToken;

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(2000, TimeUnit.MILLISECONDS, 2000, TimeUnit.MILLISECONDS, false);
    }

    @Bean
    public RequestInterceptor requestIdForwardingInterceptor() {
        return template -> {
            String target = template.feignTarget().name();
            String token = switch (target) {
                case "identity-service" -> identityToken;
                case "order-service" -> orderToken;
                case "notification-service" -> notificationToken;
                default -> "";
            };
            if (!token.isBlank()) {
                template.header("X-Service-Token", token);
            }

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                String requestId = request.getHeader("X-Request-Id");
                if (requestId == null || requestId.isBlank()) {
                    requestId = request.getHeader("X-Request-ID");
                }
                if (requestId != null && !requestId.isBlank()) {
                    template.header("X-Request-Id", requestId);
                }

                String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (authHeader != null && !authHeader.isBlank()
                        && "identity-service".equals(target)
                        && "/internal/v1/identity/auth/authorization-context".equals(template.path())) {
                    template.header(HttpHeaders.AUTHORIZATION, authHeader);
                }
            }
        };
    }
}
