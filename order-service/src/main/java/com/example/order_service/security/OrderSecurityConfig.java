package com.example.order_service.security;

import com.example.order_service.web.OrderApiExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class OrderSecurityConfig {

	@Bean
	@Order(1)
	SecurityFilterChain orderUserSecurity(HttpSecurity http, OrderAuthorization authorization,
			OrderApiExceptionHandler errors) throws Exception {
		var paths = PathPatternRequestMatcher.withDefaults();
		var checkout = new OrRequestMatcher(
				paths.matcher(HttpMethod.POST, "/api/v1/orders/checkout/preview"),
				paths.matcher(HttpMethod.POST, "/api/v1/orders/checkout/pay"));
		var inventory = new OrRequestMatcher(
				paths.matcher(HttpMethod.GET, "/api/v1/orders/seller/inventory"),
				paths.matcher(HttpMethod.PUT, "/api/v1/orders/seller/inventory/{productId}"),
				paths.matcher(HttpMethod.GET, "/api/v1/orders/seller/inventory/{productId}/movements"));
		return http.securityMatcher(new OrRequestMatcher(checkout, inventory))
				.csrf(AbstractHttpConfigurer::disable)
				.requestCache(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(new UserAuthorizationFilter(authorization, checkout, errors), AnonymousAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(checkout).hasAuthority(OrderAuthorization.CHECKOUT)
						.requestMatchers(inventory).hasAuthority(OrderAuthorization.INVENTORY)
						.anyRequest().denyAll())
				.build();
	}

	@Bean
	@Order(2)
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
						.requestMatchers(HttpMethod.POST, "/internal/v1/inventory")
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

	private static final class UserAuthorizationFilter extends OncePerRequestFilter {
		private final DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();
		private final OrderAuthorization authorization;
		private final RequestMatcher checkout;
		private final OrderApiExceptionHandler errors;

		private UserAuthorizationFilter(OrderAuthorization authorization, RequestMatcher checkout,
				OrderApiExceptionHandler errors) {
			this.authorization = authorization;
			this.checkout = checkout;
			this.errors = errors;
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
				throws ServletException, IOException {
			String permission = checkout.matches(request) ? OrderAuthorization.CHECKOUT : OrderAuthorization.INVENTORY;
			OrderAuthorization.CurrentUser user;
			try {
				user = authorization.authorize(bearer.resolve(request), permission);
			} catch (OAuth2AuthenticationException invalid) {
				errors.write(request, response, 401, "UNAUTHENTICATED", "Bearer không hợp lệ");
				return;
			} catch (OrderAuthorization.Rejected rejected) {
				errors.write(request, response, rejected.status(), rejected.getMessage(), "Không thể xác nhận quyền yêu cầu");
				return;
			}
			var context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null,
					List.of(new SimpleGrantedAuthority(permission))));
			SecurityContextHolder.setContext(context);
			chain.doFilter(request, response);
		}
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
