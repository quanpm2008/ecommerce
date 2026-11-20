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

    @Value("${internal.security.service-token:product-service-test-token-0123456789abcdef}")
    private String serviceToken;

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(2000, TimeUnit.MILLISECONDS, 2000, TimeUnit.MILLISECONDS, true);
    }

    @Bean
    public RequestInterceptor requestIdForwardingInterceptor() {
        return template -> {
            if (serviceToken != null && !serviceToken.isBlank()) {
                template.header("X-Service-Token", serviceToken);
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
                if (authHeader != null && !authHeader.isBlank()) {
                    template.header(HttpHeaders.AUTHORIZATION, authHeader);
                }
            }
        };
    }
}
