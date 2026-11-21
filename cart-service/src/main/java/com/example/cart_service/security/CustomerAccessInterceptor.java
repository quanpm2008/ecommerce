package com.example.cart_service.security;

import com.example.cart_service.client.AuthorizationContext;
import com.example.cart_service.client.IdentityGateway;
import com.example.cart_service.common.error.ApiException;
import com.example.cart_service.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards every cart endpoint with {@code cart:manage:own} (API §6.3, Database §3.1.9/§3.3.1). Identity verifies the
 * bearer token (signature, expiry, session, authzVersion) and returns the current context on every request; nothing
 * is cached (API §3.1). Only an ACTIVE customer may use a cart: a BANNED customer gets 403 ACCOUNT_BANNED whatever
 * their payment history (UC-B03.ALT-06), and Identity being down is 503, never a pass.
 */
@Component
public class CustomerAccessInterceptor implements HandlerInterceptor {

	static final String PERMISSION = "cart:manage:own";

	private final IdentityGateway identity;

	public CustomerAccessInterceptor(IdentityGateway identity) {
		this.identity = identity;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String token = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
		AuthorizationContext context = identity.authorizationContext(token);
		request.setAttribute(CurrentCustomer.ATTRIBUTE, authorize(context));
		return true;
	}

	static CurrentCustomer authorize(AuthorizationContext context) {
		if (!context.sessionActive() || context.userId() == null) {
			throw new ApiException(ErrorCode.UNAUTHENTICATED);
		}
		if (!"ACTIVE".equals(context.userStatus())) {
			throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
		}
		AuthorizationContext.CustomerAccess customer = context.customerProfile();
		if (customer != null && "BANNED".equals(customer.status())) {
			throw new ApiException(ErrorCode.ACCOUNT_BANNED);
		}
		if (!context.permissions().contains(PERMISSION)) {
			throw new ApiException(ErrorCode.FORBIDDEN);
		}
		if (customer == null) {
			throw new ApiException(ErrorCode.PROFILE_REQUIRED);
		}
		if (!"ACTIVE".equals(customer.status())) {
			throw new ApiException(ErrorCode.FORBIDDEN);
		}
		return new CurrentCustomer(context.userId(), customer.paymentBlocked());
	}

	private static String bearerToken(String authorization) {
		if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
			throw new ApiException(ErrorCode.UNAUTHENTICATED);
		}
		String token = authorization.substring(7).strip();
		if (token.isEmpty()) {
			throw new ApiException(ErrorCode.UNAUTHENTICATED);
		}
		return token;
	}

}
