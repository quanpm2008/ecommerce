package com.example.notification_service.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalClientProperties.class)
public class SecurityWebConfiguration implements WebMvcConfigurer {
    private final NotificationAccessInterceptor users;
    private final InternalAccessInterceptor services;

    public SecurityWebConfiguration(NotificationAccessInterceptor users, InternalAccessInterceptor services) {
        this.users = users;
        this.services = services;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(users).addPathPatterns("/api/**");
        registry.addInterceptor(services).addPathPatterns("/internal/**");
    }
}
