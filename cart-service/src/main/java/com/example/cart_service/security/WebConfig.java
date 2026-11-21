package com.example.cart_service.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebConfig implements WebMvcConfigurer {

	private final CustomerAccessInterceptor customerAccess;

	WebConfig(CustomerAccessInterceptor customerAccess) {
		this.customerAccess = customerAccess;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(customerAccess).addPathPatterns("/api/v1/cart", "/api/v1/cart/**");
	}

}
