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
 * One route per {@link ServiceRoute}: any method on its paths is proxied to an instance picked through Eureka
 * ({@code lb://<service>}), with the path unchanged or without {@code /api/v1} ({@link ServiceRoute#stripsBasePath()}).
 * Downstream responses, error bodies included, are returned as they are; only failures to get a response become gateway
 * errors ({@code error.GatewayErrorHandler}).
 * <p>
 * Which request headers a service gets (no client identity headers, no {@code X-Service-Token}, the gateway's own
 * {@code X-Forwarded-*}) is {@link ProxiedRequestHeaders}, applied to every route.
 */
@Configuration
class RoutesConfig {

	@Bean
	RouteLocator serviceRoutes(RouteLocatorBuilder builder) {
		RouteLocatorBuilder.Builder routes = builder.routes();
		for (ServiceRoute service : ServiceRoute.values()) {
			routes.route(service.serviceId(), route -> route
					.path(service.paths().toArray(String[]::new))
					.filters(filters -> {
						if (service.stripsBasePath()) {
							// "/api/v1" is two segments; the query string and its encoding are kept as sent.
							filters.stripPrefix(BASE_PATH_SEGMENTS);
						}
						return filters;
					})
					.uri("lb://" + service.serviceId()));
		}
		return routes.build();
	}

	private static final int BASE_PATH_SEGMENTS = ServiceRoute.BASE_PATH.substring(1).split("/").length;

	/**
	 * Connect timeout of the proxy's HTTP client as a duration ({@code gateway.proxy.connect-timeout}); the gateway's own
	 * property takes milliseconds. The read side is {@code spring.cloud.gateway.server.webflux.httpclient.response-timeout}.
	 */
	@Bean
	HttpClientCustomizer connectTimeout(@Value("${gateway.proxy.connect-timeout}") Duration timeout) {
		return client -> client.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(timeout.toMillis()));
	}

}
