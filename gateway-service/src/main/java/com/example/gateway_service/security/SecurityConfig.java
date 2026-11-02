package com.example.gateway_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless, Bearer-only security for the gateway: no HTTP session, cookies, CSRF, form login or basic auth.
 * <p>
 * JWT verification is not wired yet (checklist §3). Until then every request passes this chain, which is safe only
 * because the gateway has no routes yet; downstream services verify the token themselves in any case (API §2.3).
 */
@Configuration
class SecurityConfig {

	@Bean
	SecurityFilterChain gatewaySecurity(HttpSecurity http) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
		return http.build();
	}

}
