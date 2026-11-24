package com.example.order_service.security;

import com.example.order_service.exception.OrderApiExceptionHandler;
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
import java.util.Collections;

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
		var checkoutRead = new OrRequestMatcher(
				paths.matcher(HttpMethod.GET, "/api/v1/orders/checkout-sessions/current"),
				paths.matcher(HttpMethod.GET, "/api/v1/orders/checkout-sessions/{checkoutSessionId}"));
		var inventory = new OrRequestMatcher(
				paths.matcher(HttpMethod.GET, "/api/v1/orders/seller/inventory"),
				paths.matcher(HttpMethod.PUT, "/api/v1/orders/seller/inventory/{productId}"),
				paths.matcher(HttpMethod.GET, "/api/v1/orders/seller/inventory/{productId}/movements"));
		var customerDetail = paths.matcher(HttpMethod.GET, "/api/v1/orders/me/{orderId}");
		var customerList = paths.matcher(HttpMethod.GET, "/api/v1/orders/me");
		var sellerList = paths.matcher(HttpMethod.GET, "/api/v1/orders/seller/orders");
		var adminRead = new OrRequestMatcher(paths.matcher(HttpMethod.GET, "/api/v1/orders/admin/orders"),
				paths.matcher(HttpMethod.GET, "/api/v1/orders/admin/orders/{id}"));
		var customerCancel = paths.matcher(HttpMethod.POST, "/api/v1/orders/me/{orderId}/cancel");
		var customerConfirm = paths.matcher(HttpMethod.POST, "/api/v1/orders/me/{orderId}/confirm-receipt");
		var sellerDetail = paths.matcher(HttpMethod.GET, "/api/v1/orders/seller/orders/{orderId}");
		var seller = new OrRequestMatcher(sellerDetail, sellerList,
				paths.matcher(HttpMethod.POST, "/api/v1/orders/seller/orders/{orderId}/start-packing"),
				paths.matcher(HttpMethod.POST, "/api/v1/orders/seller/orders/{orderId}/mark-delivered"),
				paths.matcher(HttpMethod.POST, "/api/v1/orders/seller/orders/{orderId}/reject"));
		var lifecycle = new OrRequestMatcher(customerDetail, customerList, customerCancel, customerConfirm, seller);
		var detail = new OrRequestMatcher(customerDetail, sellerDetail);
		var rules = List.of(new Route(checkout, OrderAuthorization.CHECKOUT),
				new Route(new OrRequestMatcher(checkoutRead, customerDetail, customerList), OrderAuthorization.CHECKOUT_READ),
				new Route(adminRead, OrderAuthorization.OPERATIONS_READ),
				new Route(inventory, OrderAuthorization.INVENTORY), new Route(customerCancel, OrderAuthorization.ORDER_CANCEL),
				new Route(customerConfirm, OrderAuthorization.ORDER_CONFIRM), new Route(seller, OrderAuthorization.FULFILL));
		return http.securityMatcher(new OrRequestMatcher(checkout, checkoutRead, inventory, lifecycle, adminRead))
				.csrf(AbstractHttpConfigurer::disable)
				.requestCache(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(new UserAuthorizationFilter(authorization, rules, lifecycle, detail, errors),
						AnonymousAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> {
					rules.forEach(route -> auth.requestMatchers(route.matcher()).hasAuthority(route.permission()));
					auth.anyRequest().denyAll();
				})
				.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain orderSecurity(HttpSecurity http, OrderApiExceptionHandler errors,
			@Value("${order.internal.tokens.product}") String productToken,
			@Value("${order.internal.tokens.cart}") String cartToken,
			@Value("${order.internal.tokens.payment:}") String paymentToken,
			@Value("${order.internal.tokens.finance:}") String financeToken,
			@Value("${order.internal.tokens.identity:}") String identityToken) throws Exception {
		var tokens = List.of(productToken, cartToken, paymentToken, financeToken, identityToken).stream()
				.filter(token -> !token.isBlank()).toList();
		if (tokens.stream().distinct().count() != tokens.size()) {
			throw new IllegalStateException("Order service tokens must be distinct per caller");
		}
		return http.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(new ServiceTokenFilter(productToken, cartToken, paymentToken, financeToken, identityToken),
						AnonymousAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, "/internal/v1/inventory/availability")
						.hasAnyAuthority("service:product-service", "service:cart-service")
						.requestMatchers(HttpMethod.GET, "/internal/v1/inventory/details")
						.hasAuthority("service:product-service")
						.requestMatchers(HttpMethod.POST, "/internal/v1/inventory")
						.hasAuthority("service:product-service")
						.requestMatchers(HttpMethod.GET, "/internal/v1/orders/checkout-sessions/{id}/payable")
						.hasAuthority("service:payment-service")
						.requestMatchers(HttpMethod.GET, "/internal/v1/orders/collection-reviews")
						.hasAuthority("service:payment-service")
						.requestMatchers(HttpMethod.POST, "/internal/v1/orders/checkout-sessions/{id}/mark-handled")
						.hasAuthority("service:payment-service")
						.requestMatchers(HttpMethod.GET, "/internal/v1/orders/reconciliation-totals")
						.hasAuthority("service:finance-service")
						.requestMatchers(HttpMethod.GET, "/internal/v1/orders/sellers/{id}/open-orders")
						.hasAuthority("service:identity-service")
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.anyRequest().denyAll())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, failure) ->
								errors.write(request, response, 401, "UNAUTHENTICATED", "Chưa xác thực"))
						.accessDeniedHandler((request, response, failure) ->
								errors.write(request, response, 403, "FORBIDDEN", "Không có quyền truy cập")))
				.build();
	}

	private record Route(RequestMatcher matcher, String permission) {}

	private static final class UserAuthorizationFilter extends OncePerRequestFilter {
		private final DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();
		private final OrderAuthorization authorization;
		private final List<Route> rules;
		private final RequestMatcher lifecycle;
		private final RequestMatcher detail;
		private final OrderApiExceptionHandler errors;

		private UserAuthorizationFilter(OrderAuthorization authorization, List<Route> rules, RequestMatcher lifecycle,
				RequestMatcher detail, OrderApiExceptionHandler errors) {
			this.authorization = authorization;
			this.rules = rules;
			this.lifecycle = lifecycle;
			this.detail = detail;
			this.errors = errors;
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
				throws ServletException, IOException {
			String permission = rules.stream().filter(route -> route.matcher().matches(request))
					.findFirst().orElseThrow().permission();
			OrderAuthorization.CurrentUser user;
			try {
				user = authorization.authorize(bearer.resolve(request), permission, lifecycle.matches(request), detail.matches(request));
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
		private final String paymentToken;
		private final String financeToken;
		private final String identityToken;

		private ServiceTokenFilter(String productToken, String cartToken, String paymentToken, String financeToken,
				String identityToken) {
			this.productToken = productToken;
			this.cartToken = cartToken;
			this.paymentToken = paymentToken;
			this.financeToken = financeToken;
			this.identityToken = identityToken;
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
				throws ServletException, IOException {
			var tokens = Collections.list(request.getHeaders("X-Service-Token"));
			String token = tokens.size() == 1 ? tokens.getFirst() : null;
			if (matches(productToken, token)) {
				authenticate("product-service");
			} else if (matches(cartToken, token)) {
				authenticate("cart-service");
			} else if (matches(paymentToken, token)) {
				authenticate("payment-service");
			} else if (matches(financeToken, token)) {
				authenticate("finance-service");
			} else if (matches(identityToken, token)) {
				authenticate("identity-service");
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
