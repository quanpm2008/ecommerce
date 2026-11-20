package com.example.gateway_service.security;

import java.util.Arrays;

import com.example.gateway_service.routing.ServiceRoute;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.session.DisableEncodeUrlFilter;

/**
 * Stateless, Bearer-only security for the gateway: no HTTP session, cookies, CSRF, form login or basic auth.
 * <ol>
 * <li>{@link InternalPathFilter} first: {@code /internal/**} is a 404 before anything else looks at the request.</li>
 * <li>Any request carrying an {@code Authorization} header has it checked ({@link JwtConfig},
 * {@link StrictBearerTokenResolver}); a bad one is a 401 even on a public endpoint.</li>
 * <li>Routed paths need a valid token except the spec's {@link PublicEndpoints}. Everything else is not routed (health,
 * {@code /error}, unknown paths that answer 404) and needs none.</li>
 * </ol>
 * The gateway does not decide permissions: each service verifies the token again and asks Identity for the current
 * authorization context (API §2.3).
 */
@Configuration
class SecurityConfig {

	@Bean
	SecurityFilterChain gatewaySecurity(HttpSecurity http, AuthenticationErrors errors) throws Exception {
		String[] routedPaths = Arrays.stream(ServiceRoute.values())
				.flatMap(route -> route.paths().stream())
				.toArray(String[]::new);
		http.addFilterBefore(new InternalPathFilter(), DisableEncodeUrlFilter.class)
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.oauth2ResourceServer(resourceServer -> resourceServer
						.bearerTokenResolver(new StrictBearerTokenResolver())
						.authenticationEntryPoint(errors)
						.jwt(jwt -> {
						})
						.withObjectPostProcessor(reportKeyFailuresAsUnavailable(errors)))
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(errors))
				.authorizeHttpRequests(auth -> {
					PublicEndpoints.ALL.forEach(endpoint ->
							auth.requestMatchers(endpoint.method(), endpoint.path()).permitAll());
					auth.requestMatchers(routedPaths).authenticated();
					auth.anyRequest().permitAll();
				});
		return http.build();
	}

	/**
	 * By default a token that can't be checked at all (JWKS unreachable) is rethrown and becomes a 500; hand it to
	 * {@link AuthenticationErrors} instead, which answers 503.
	 */
	private static ObjectPostProcessor<BearerTokenAuthenticationFilter> reportKeyFailuresAsUnavailable(
			AuthenticationErrors errors) {
		return new ObjectPostProcessor<>() {
			@Override
			public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
				AuthenticationEntryPointFailureHandler failures = new AuthenticationEntryPointFailureHandler(errors);
				failures.setRethrowAuthenticationServiceException(false);
				filter.setAuthenticationFailureHandler(failures);
				return filter;
			}
		};
	}

}
