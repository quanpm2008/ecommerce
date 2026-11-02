package com.example.gateway_service.routing;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.removeRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * One route per {@link ServiceRoute}: any method on its paths is proxied unchanged to an instance picked through
 * Eureka ({@code lb://<service>}). Downstream responses, error bodies included, are returned as they are; only failures
 * to get a response are turned into gateway errors by {@link DownstreamErrors}.
 * <p>
 * A client-sent {@code X-Service-Token} (the credential services use on {@code /internal}) is removed, so the gateway can
 * never carry one; services never accept it on public paths anyway.
 */
@Configuration
class RoutesConfig {

	@Bean
	RouterFunction<ServerResponse> serviceRoutes(DownstreamErrors errors) {
		return Arrays.stream(ServiceRoute.values())
				.map(service -> serviceRoute(service, errors))
				.reduce(RouterFunction::and)
				.orElseThrow();
	}

	static final String SERVICE_TOKEN = "X-Service-Token";

	private static RouterFunction<ServerResponse> serviceRoute(ServiceRoute service, DownstreamErrors errors) {
		RequestPredicate paths = service.paths().stream()
				.map(RequestPredicates::path)
				.reduce(RequestPredicate::or)
				.orElseThrow();
		// The error handler is the first filter so it wraps the load-balancer filter too ("no instance" is thrown
		// there). The builder's onError(...) would not: Spring applies those inside every filter, around the handler only.
		return route(service.serviceId())
				.route(paths, http())
				.before(removeRequestHeader(SERVICE_TOKEN))
				.filter(HandlerFilterFunction.ofErrorHandler(Exception.class::isInstance, errors::toResponse))
				.filter(lb(service.serviceId()))
				.build();
	}

}
