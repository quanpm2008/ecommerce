package com.example.identity_service.common.security;

import com.example.identity_service.session.JwksController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless Bearer-token API: no HTTP session, no cookies, so no CSRF. Public endpoints are listed explicitly;
 * everything else needs a valid access token. Permission checks happen per endpoint, never by role name.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http, SecurityErrorHandler errors) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health", "/hello", JwksController.PATH).permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults())
						.authenticationEntryPoint(errors)
						.accessDeniedHandler(errors))
				.exceptionHandling(ex -> ex.authenticationEntryPoint(errors).accessDeniedHandler(errors));
		return http.build();
	}

}
