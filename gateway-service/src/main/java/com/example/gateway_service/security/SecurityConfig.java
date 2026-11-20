package com.example.gateway_service.security;

import java.util.Arrays;

import com.example.gateway_service.error.ErrorCode;
import com.example.gateway_service.error.ErrorWriter;
import com.example.gateway_service.routing.ServiceRoute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.firewall.ServerExchangeRejectedHandler;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

/**
 * Stateless, Bearer-only security for the gateway: no session, CSRF, form login or basic auth.
 * <ol>
 * <li>{@link InternalPathFilter} first: {@code /internal/**} is a 404 before anything else looks at the request.</li>
 * <li>Any request carrying an {@code Authorization} header has it checked ({@link JwtConfig},
 * {@link StrictBearerTokenConverter}); a bad one is a 401 even on a public endpoint.</li>
 * <li>Routed paths need a valid token except the spec's {@link PublicEndpoints}. Everything else is not routed (health,
 * unknown paths that answer 404) and needs none.</li>
 * </ol>
 * Requests Spring Security's firewall rejects (encoded {@code /}, {@code ;}, {@code ..}, ...) get a 400 envelope. The
 * gateway does not decide permissions: each service verifies the token again and asks Identity for the current
 * authorization context (API §2.3).
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	@Bean
	SecurityWebFilterChain gatewaySecurity(ServerHttpSecurity http, AuthenticationErrors authenticationErrors,
			ErrorWriter errors) {
		String[] routedPaths = Arrays.stream(ServiceRoute.values())
				.flatMap(route -> route.paths().stream())
				.toArray(String[]::new);
		// By default a token that can't be checked at all (JWKS unreachable) is rethrown and becomes a 500; hand it to
		// AuthenticationErrors instead, which answers 503.
		ServerAuthenticationEntryPointFailureHandler failures =
				new ServerAuthenticationEntryPointFailureHandler(authenticationErrors);
		failures.setRethrowAuthenticationServiceException(false);
		http.addFilterAt(new InternalPathFilter(errors), SecurityWebFiltersOrder.FIRST)
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				.logout(ServerHttpSecurity.LogoutSpec::disable)
				.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
				.requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
				.oauth2ResourceServer(resourceServer -> resourceServer
						.bearerTokenConverter(new StrictBearerTokenConverter())
						.authenticationEntryPoint(authenticationErrors)
						.authenticationFailureHandler(failures)
						.jwt(Customizer.withDefaults()))
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationErrors))
				.authorizeExchange(auth -> {
					PublicEndpoints.ALL.forEach(endpoint ->
							auth.pathMatchers(endpoint.method(), endpoint.path()).permitAll());
					auth.pathMatchers(routedPaths).authenticated();
					auth.anyExchange().permitAll();
				});
		return http.build();
	}

	/** Requests the firewall rejects before any filter runs: 400 {@code VALIDATION_ERROR} in the envelope. */
	@Bean
	ServerExchangeRejectedHandler rejectedRequests(ErrorWriter errors) {
		return (exchange, rejected) -> {
			log.debug("{} {}: rejected by the firewall ({})", exchange.getRequest().getMethod(),
					exchange.getRequest().getURI().getRawPath(), rejected.getMessage());
			return errors.write(exchange, ErrorCode.VALIDATION_ERROR);
		};
	}

}
