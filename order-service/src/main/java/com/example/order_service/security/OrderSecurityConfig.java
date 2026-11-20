package com.example.order_service.security;

import com.example.order_service.web.OrderApiExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class OrderSecurityConfig {

	@Bean
	SecurityFilterChain orderSecurity(HttpSecurity http, OrderApiExceptionHandler errors,
			@Value("${order.internal.tokens.product}") String productToken,
			@Value("${order.internal.tokens.cart}") String cartToken) throws Exception {
		if (!productToken.isBlank() && productToken.equals(cartToken)) {
			throw new IllegalStateException("Order service tokens must be distinct per caller");
		}
		return http.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(new ServiceTokenFilter(productToken, cartToken), AnonymousAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, "/internal/v1/inventory/availability")
						.hasAnyAuthority("service:product-service", "service:cart-service")
						.requestMatchers(HttpMethod.GET, "/internal/v1/inventory/details")
						.hasAuthority("service:product-service")
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.anyRequest().denyAll())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, failure) ->
								errors.write(request, response, 401, "UNAUTHENTICATED", "Chưa xác thực"))
						.accessDeniedHandler((request, response, failure) ->
								errors.write(request, response, 403, "FORBIDDEN", "Không có quyền truy cập")))
				.build();
	}

	private static final class ServiceTokenFilter extends OncePerRequestFilter {

		private final String productToken;
		private final String cartToken;

		private ServiceTokenFilter(String productToken, String cartToken) {
			this.productToken = productToken;
			this.cartToken = cartToken;
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
				throws ServletException, IOException {
			String token = request.getHeader("X-Service-Token");
			if (matches(productToken, token)) {
				authenticate("product-service");
			} else if (matches(cartToken, token)) {
				authenticate("cart-service");
			}
			chain.doFilter(request, response);
		}

		private static boolean matches(String expected, String actual) {
			return !expected.isBlank() && actual != null && MessageDigest.isEqual(
					expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
		}

		private static void authenticate(String caller) {
			var context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(caller, null,
					List.of(new SimpleGrantedAuthority("service:" + caller))));
			SecurityContextHolder.setContext(context);
		}
	}
}
