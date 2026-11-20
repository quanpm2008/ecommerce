package com.example.gateway_service.routing;

import java.time.Duration;

import io.netty.channel.ChannelOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One route per {@link ServiceRoute}: any method on its paths is proxied unchanged to an instance picked through
 * Eureka ({@code lb://<service>}). Downstream responses, error bodies included, are returned as they are; only failures
 * to get a response become gateway errors ({@code error.GatewayErrorHandler}).
 * <p>
 * A client-sent {@code X-Service-Token} (the credential services use on {@code /internal}) is removed, so the gateway can
 * never carry one; services never accept it on public paths anyway.
 */
@Configuration
class RoutesConfig {

	static final String SERVICE_TOKEN = "X-Service-Token";

	@Bean
	RouteLocator serviceRoutes(RouteLocatorBuilder builder) {
		RouteLocatorBuilder.Builder routes = builder.routes();
		for (ServiceRoute service : ServiceRoute.values()) {
			routes.route(service.serviceId(), route -> route
					.path(service.paths().toArray(String[]::new))
					.filters(filters -> filters.removeRequestHeader(SERVICE_TOKEN))
					.uri("lb://" + service.serviceId()));
		}
		return routes.build();
	}

	/**
	 * Connect timeout of the proxy's HTTP client as a duration ({@code gateway.proxy.connect-timeout}); the gateway's own
	 * property takes milliseconds. The read side is {@code spring.cloud.gateway.server.webflux.httpclient.response-timeout}.
	 */
	@Bean
	HttpClientCustomizer connectTimeout(@Value("${gateway.proxy.connect-timeout}") Duration timeout) {
		return client -> client.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(timeout.toMillis()));
	}

}
