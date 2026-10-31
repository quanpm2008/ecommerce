package com.example.identity_service.internal;

import com.example.identity_service.common.security.SecurityErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * The service-to-service chain for {@code /internal/**} (API §7.1), checked before the public one. Only a service
 * credential authenticates here: a user's Bearer token alone is not enough (it is only data for
 * authorization-context), and being inside the network is not either. Each endpoint then checks its consumer
 * allowlist with {@code @PreAuthorize("hasAnyAuthority('service:...')")}. The gateway must also block
 * {@code /internal/**} from outside.
 */
@Configuration(proxyBeanMethods = false)
public class InternalSecurityConfig {

	@Bean
	@Order(1)
	SecurityFilterChain internalSecurity(HttpSecurity http, ServiceCredentials credentials,
			SecurityErrorHandler errors) throws Exception {
		http.securityMatcher("/internal/**")
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(new ServiceTokenFilter(credentials), AnonymousAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
				.exceptionHandling(ex -> ex.authenticationEntryPoint(errors).accessDeniedHandler(errors));
		return http.build();
	}

}
