package com.example.gateway_service.web;

import io.micrometer.context.ContextRegistry;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Settles the request id before anything else runs (checklist §4, API §3.10): keeps a single safe id the client sent,
 * otherwise generates one. The id replaces whatever {@code X-Request-Id} the client sent on the forwarded request, is set
 * on every response (also errors, 404s and 429s, written later by other filters), and is in the MDC of every log line
 * for the request: it goes into the Reactor context, and with {@code spring.reactor.context-propagation=auto} the MDC
 * follows it across threads.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements WebFilter {

	static {
		ContextRegistry.getInstance().registerThreadLocalAccessor(RequestIds.MDC_KEY,
				() -> MDC.get(RequestIds.MDC_KEY),
				id -> MDC.put(RequestIds.MDC_KEY, id),
				() -> MDC.remove(RequestIds.MDC_KEY));
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String sent = RequestIds.sent(exchange.getRequest());
		String id = sent != null ? sent : RequestIds.generate();
		exchange.getAttributes().put(RequestIds.ATTRIBUTE, id);
		ServerHttpResponse response = exchange.getResponse();
		// At commit time, so it is there whoever writes the response and overrides a service's echo of it.
		response.beforeCommit(() -> {
			response.getHeaders().set(RequestIds.HEADER, id);
			return Mono.empty();
		});
		ServerWebExchange withId = exchange.mutate()
				.request(request -> request.headers(headers -> headers.set(RequestIds.HEADER, id)))
				.build();
		return chain.filter(withId).contextWrite(Context.of(RequestIds.MDC_KEY, id));
	}

}
