package com.example.identity_service.common.security;

import com.example.identity_service.auth.AuthController;
import com.example.identity_service.region.RegionController;
import com.example.identity_service.session.JwksController;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless Bearer-token API: no HTTP session, no cookies, so no CSRF. Public endpoints are listed explicitly;
 * everything else needs a valid access token. Authorities are the caller's current permissions
 * ({@link PermissionAuthentication}); endpoints check them with {@code @PreAuthorize}, never by role name.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

	private static final String[] PUBLIC_POSTS = { AuthController.PATH + "/register",
			AuthController.PATH + "/register-seller", AuthController.PATH + "/login", AuthController.PATH + "/refresh",
			AuthController.PATH + "/password/forgot", AuthController.PATH + "/password/reset" };

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http, SecurityErrorHandler errors,
			PermissionAuthentication permissions) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health", "/hello", JwksController.PATH).permitAll()
						.requestMatchers(HttpMethod.POST, PUBLIC_POSTS).permitAll()
						.requestMatchers(HttpMethod.GET, RegionController.PATH + "/**").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(permissions::authenticate))
						.authenticationEntryPoint(errors)
						.accessDeniedHandler(errors))
				.exceptionHandling(ex -> ex.authenticationEntryPoint(errors).accessDeniedHandler(errors));
		return http.build();
	}

}
