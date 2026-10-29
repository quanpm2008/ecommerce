package com.example.identity_service.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Propagates {@code X-Request-Id} (API §3.10). The gateway generates the id; this service never invents one. A safe
 * incoming value is echoed on the response and exposed to logs (MDC) and error responses. A missing or unsafe value
 * is ignored, so the request has no id and {@code error.requestId} is null, which makes a gateway or caller that
 * fails to forward the header visible.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";
	public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
	private static final String MDC_KEY = "requestId";
	private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String requestId = request.getHeader(HEADER);
		if (requestId == null || !SAFE_ID.matcher(requestId).matches()) {
			chain.doFilter(request, response);
			return;
		}
		request.setAttribute(ATTRIBUTE, requestId);
		response.setHeader(HEADER, requestId);
		MDC.put(MDC_KEY, requestId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}

}
