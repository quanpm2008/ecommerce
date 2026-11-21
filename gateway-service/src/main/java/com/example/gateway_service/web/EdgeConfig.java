package com.example.gateway_service.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@link EdgeProperties} for the edge filters ({@link CorsFilter}, {@link RateLimitFilter}, {@link BodySizeFilter}). */
@Configuration
@EnableConfigurationProperties(EdgeProperties.class)
class EdgeConfig {

}
