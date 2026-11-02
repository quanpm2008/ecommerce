package com.example.gateway_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.DisableEncodeUrlFilter;

/**
 * Stateless, Bearer-only security for the gateway: no HTTP session, cookies, CSRF, form login or basic auth.
 * {@link InternalPathFilter} comes first and turns away {@code /internal/**} before anything else looks at the request.
 * <p>
 * JWT verification is not wired yet (checklist §3). Until then every routed request passes this chain; services verify
 * the token themselves in any case (API §2.3).
 */
@Configuration
class SecurityConfig {

	@Bean
	SecurityFilterChain gatewaySecurity(HttpSecurity http) throws Exception {
		http.addFilterBefore(new InternalPathFilter(), DisableEncodeUrlFilter.class)
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
		return http.build();
	}

}
