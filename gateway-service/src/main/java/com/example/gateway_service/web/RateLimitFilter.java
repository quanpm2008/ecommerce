package com.example.gateway_service.web;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import com.example.gateway_service.error.ErrorCode;
import com.example.gateway_service.error.ErrorWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Per-client-IP rate limits (checklist §7, API §3.4 N-04, decision 0002): a token bucket per IP and tier, refilled
 * evenly over the window. Over the limit → 429 {@code RATE_LIMITED} in the envelope with {@code Retry-After} (whole
 * seconds until the next request is allowed).
 * <ul>
 * <li>Sensitive tier ({@code gateway.rate-limit.sensitive}): {@link #SENSITIVE} sign-in, sign-up, refresh, password and
 * seller-reapplication endpoints. They count only there.</li>
 * <li>General tier ({@code gateway.rate-limit.general}): every other request except {@code /actuator/health} (probes);
 * CORS preflights are answered before this filter and not counted.</li>
 * </ul>
 * The IP is the connection's address, never a client-sent {@code X-Forwarded-For}, so faking that header doesn't reset
 * the count. Counters live in this instance's memory: with N gateway instances the real limit is up to N times the
 * configured one (SAD C.5; no shared store, REQ-ENG-010). identity-service keeps its own per-IP limits behind this.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
class RateLimitFilter implements WebFilter {

	private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

	static final Set<String> SENSITIVE = Set.of("/api/v1/identity/auth/login", "/api/v1/identity/auth/register",
			"/api/v1/identity/auth/register-seller", "/api/v1/identity/auth/refresh",
			"/api/v1/identity/auth/password/forgot", "/api/v1/identity/auth/password/reset",
			"/api/v1/identity/auth/seller-reapplication/challenge", "/api/v1/identity/auth/seller-reapplication");

	private static final String HEALTH = "/actuator/health";

	/** Full buckets are dropped once in a while so idle IPs don't keep memory. */
	private static final int SWEEP_EVERY = 4096;

	private final Tier sensitive;

	private final Tier general;

	private final ErrorWriter errors;

	private final LongSupplier nanoTime;

	private final AtomicLong requests = new AtomicLong();

	@Autowired
	RateLimitFilter(EdgeProperties properties, ErrorWriter errors) {
		this(properties, errors, System::nanoTime);
	}

	RateLimitFilter(EdgeProperties properties, ErrorWriter errors, LongSupplier nanoTime) {
		this.sensitive = new Tier("sensitive", properties.rateLimit().sensitive());
		this.general = new Tier("general", properties.rateLimit().general());
		this.errors = errors;
		this.nanoTime = nanoTime;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		ServerHttpRequest request = exchange.getRequest();
		String path = request.getPath().value();
		if (path.equals(HEALTH)) {
			return chain.filter(exchange);
		}
		Tier tier = request.getMethod() == HttpMethod.POST && SENSITIVE.contains(path) ? sensitive : general;
		String client = clientIp(request);
		long now = nanoTime.getAsLong();
		long waitNanos = tier.tryTake(client, now);
		if (requests.incrementAndGet() % SWEEP_EVERY == 0) {
			sensitive.sweep(now);
			general.sweep(now);
		}
		if (waitNanos <= 0) {
			return chain.filter(exchange);
		}
		long retryAfter = Math.max(1, Duration.ofNanos(waitNanos).toSeconds()
				+ (Duration.ofNanos(waitNanos).toNanosPart() > 0 ? 1 : 0));
		log.info("{} {}: rate limited ({} tier, client {}), retry after {} s", request.getMethod(), path, tier.name,
				client, retryAfter);
		exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
		return errors.write(exchange, ErrorCode.RATE_LIMITED);
	}

	/** Forgets every count (tests). */
	void clear() {
		sensitive.buckets.clear();
		general.buckets.clear();
	}

	private static String clientIp(ServerHttpRequest request) {
		InetSocketAddress remote = request.getRemoteAddress();
		return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "unknown";
	}

	/** One tier's buckets: capacity {@code requests}, refilled at {@code requests / window}. */
	private static final class Tier {

		private final String name;

		private final double capacity;

		private final double tokensPerNano;

		private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

		Tier(String name, EdgeProperties.Tier limits) {
			this.name = name;
			this.capacity = limits.requests();
			this.tokensPerNano = limits.requests() / (double) limits.window().toNanos();
		}

		/** Takes one token for {@code client}: 0 if allowed, else the nanoseconds until one is available. */
		long tryTake(String client, long now) {
			Bucket bucket = buckets.computeIfAbsent(client, key -> new Bucket(capacity, now));
			synchronized (bucket) {
				bucket.refill(now, capacity, tokensPerNano);
				if (bucket.tokens >= 1) {
					bucket.tokens -= 1;
					return 0;
				}
				return (long) Math.ceil((1 - bucket.tokens) / tokensPerNano);
			}
		}

		void sweep(long now) {
			buckets.entrySet().removeIf(entry -> {
				Bucket bucket = entry.getValue();
				synchronized (bucket) {
					bucket.refill(now, capacity, tokensPerNano);
					return bucket.tokens >= capacity;
				}
			});
		}

	}

	private static final class Bucket {

		double tokens;

		long updatedAt;

		Bucket(double tokens, long now) {
			this.tokens = tokens;
			this.updatedAt = now;
		}

		void refill(long now, double capacity, double tokensPerNano) {
			if (now > updatedAt) {
				tokens = Math.min(capacity, tokens + (now - updatedAt) * tokensPerNano);
				updatedAt = now;
			}
		}

	}

}
