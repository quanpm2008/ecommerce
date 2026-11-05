package com.example.identity_service.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates {@code /internal/**} callers by {@code X-Service-Token}. A known token makes the request
 * authenticated as that service with the single authority {@code service:<name>}; anything else leaves it
 * anonymous, so the chain answers 401. Deliberately not a bean, so it only runs inside the internal chain.
 */
class ServiceTokenFilter extends OncePerRequestFilter {

	static final String HEADER = "X-Service-Token";

	private final ServiceCredentials credentials;

	ServiceTokenFilter(ServiceCredentials credentials) {
		this.credentials = credentials;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		credentials.serviceFor(request.getHeader(HEADER)).ifPresent(service -> {
			var authentication = new PreAuthenticatedAuthenticationToken(service, null,
					List.of(new SimpleGrantedAuthority(ServiceCredentials.AUTHORITY_PREFIX + service)));
			var context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
		});
		chain.doFilter(request, response);
	}

}
