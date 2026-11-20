package com.example.gateway_service.routing;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * The request headers a service gets (checklist §4, API §2.3), applied last to every proxied request:
 * <ul>
 * <li>Removed: identity headers a client could forge ({@code X-User-Id}, {@code X-Actor-Id}, {@code X-Actor-Type},
 * {@code X-Permissions}); services take the user from the verified token's {@code sub}. Also the service credential
 * {@code X-Service-Token}, so the gateway can never carry one.</li>
 * <li>Replaced: {@code Forwarded} and {@code X-Forwarded-*}. The gateway is the edge, so nothing a client sends there is
 * trusted: {@code X-Forwarded-For} is exactly the address of the connection, so per-IP limits downstream (identity's
 * register, forgot-password, login) see the real client and cannot be dodged by faking the header.
 * {@code X-Forwarded-Proto}, {@code -Host} and {@code -Port} describe the request the gateway received.</li>
 * </ul>
 * Everything else ({@code Authorization}, {@code Content-Type}, {@code Idempotency-Key}, {@code Accept-Language},
 * {@code If-Match}, {@code X-Request-Id}, cookies) passes through; hop-by-hop headers are removed by the gateway's own
 * filter and {@code Host} is the service's. The gateway's own forwarded-header filters are turned off
 * ({@code spring.cloud.gateway.server.webflux.x-forwarded.enabled=false}, {@code forwarded.enabled=false}).
 */
@Component
class ProxiedRequestHeaders implements HttpHeadersFilter, Ordered {

	private static final Set<String> REMOVED = Set.of("x-user-id", "x-actor-id", "x-actor-type", "x-permissions",
			"x-service-token", "forwarded");

	@Override
	public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
		HttpHeaders headers = new HttpHeaders();
		input.forEach((name, values) -> {
			String lower = name.toLowerCase(Locale.ROOT);
			if (!REMOVED.contains(lower) && !lower.startsWith("x-forwarded-")) {
				headers.addAll(name, values);
			}
		});
		ServerHttpRequest request = exchange.getRequest();
		InetSocketAddress client = request.getRemoteAddress();
		if (client != null && client.getAddress() != null) {
			headers.set("X-Forwarded-For", client.getAddress().getHostAddress());
		}
		URI uri = request.getURI();
		headers.set("X-Forwarded-Proto", uri.getScheme());
		String host = request.getHeaders().getFirst(HttpHeaders.HOST);
		if (host != null) {
			headers.set("X-Forwarded-Host", host);
		}
		if (uri.getPort() > 0) {
			headers.set("X-Forwarded-Port", Integer.toString(uri.getPort()));
		}
		return headers;
	}

	@Override
	public boolean supports(Type type) {
		return type == Type.REQUEST;
	}

	/** After every other request-header filter, so nothing adds to or appends after these values. */
	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

}
