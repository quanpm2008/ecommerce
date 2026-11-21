package com.example.gateway_service.routing;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * The response headers taken from a service: its own {@code Access-Control-*} headers are dropped, because CORS is
 * answered once, by the gateway ({@code web.CorsFilter}), and two {@code Access-Control-Allow-Origin} values would make
 * the browser refuse the response. A service's {@code Vary} is merged with the gateway's ({@code Origin}, ...) instead
 * of replacing it. {@code Server} and {@code X-Powered-By} are dropped too (checklist §8); the gateway's own Netty server
 * sends neither.
 */
@Component
class ProxiedResponseHeaders implements HttpHeadersFilter, Ordered {

	/** Headers that only tell an attacker which server software runs behind the gateway. */
	private static final Set<String> STACK_HEADERS = Set.of("server", "x-powered-by", "x-application-context");

	@Override
	public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
		HttpHeaders headers = new HttpHeaders();
		input.forEach((name, values) -> {
			String lower = name.toLowerCase(Locale.ROOT);
			if (!lower.startsWith("access-control-") && !STACK_HEADERS.contains(lower)) {
				headers.addAll(name, values);
			}
		});
		Set<String> vary = new LinkedHashSet<>(exchange.getResponse().getHeaders().getVary());
		vary.addAll(headers.getVary());
		if (!vary.isEmpty()) {
			headers.setVary(vary.stream().toList());
		}
		return headers;
	}

	@Override
	public boolean supports(Type type) {
		return type == Type.RESPONSE;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

}
