package com.example.gateway_service.web;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * One access-log line per request (checklist §8), logger {@value #LOGGER}: method, path, status, time until the response
 * was committed, client IP, with the request id from the MDC. It is written when the response is committed, so the
 * status is final even when an error handler writes the response after the filters have finished; a request the
 * client gave up on before any response is logged as {@code cancelled}. Requests refused at the edge (CORS, rate limit,
 * body size, firewall, {@code /internal}, bad token) are logged too.
 * <p>
 * Never logged: the query string (it may carry tokens), headers ({@code Authorization}, cookies) and bodies (passwords,
 * reset tokens).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class AccessLogFilter implements WebFilter {

	static final String LOGGER = "gateway.access";

	private static final Logger log = LoggerFactory.getLogger(LOGGER);

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		long start = System.nanoTime();
		AtomicBoolean logged = new AtomicBoolean();
		ServerHttpResponse response = exchange.getResponse();
		response.beforeCommit(() -> {
			HttpStatusCode status = response.getStatusCode();
			write(exchange, logged, start, Integer.toString(status != null ? status.value() : 200));
			return Mono.empty();
		});
		return chain.filter(exchange).doFinally(signal -> {
			if (signal == SignalType.CANCEL && !response.isCommitted()) {
				write(exchange, logged, start, "cancelled");
			}
		});
	}

	private static void write(ServerWebExchange exchange, AtomicBoolean logged, long start, String outcome) {
		if (!logged.compareAndSet(false, true)) {
			return;
		}
		ServerHttpRequest request = exchange.getRequest();
		long millis = (System.nanoTime() - start) / 1_000_000;
		RequestIds.withMdc(exchange, () -> log.info("{} {} {} {}ms client={}", request.getMethod(),
				request.getPath().value(), outcome, millis, clientIp(request)));
	}

	private static String clientIp(ServerHttpRequest request) {
		InetSocketAddress remote = request.getRemoteAddress();
		return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "unknown";
	}

}
