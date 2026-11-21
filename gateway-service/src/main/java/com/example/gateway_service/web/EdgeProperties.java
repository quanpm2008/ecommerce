package com.example.gateway_service.web;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * What the gateway allows at the edge ({@code gateway.*}): CORS origins, request body size and rate limits. Values and
 * reasons: decisions 0001 (cookie, one SPA origin) and 0002 (limits) in {@code docs/decisions/}.
 */
@ConfigurationProperties("gateway")
public record EdgeProperties(Cors cors, Limits limits, RateLimit rateLimit) {

	public EdgeProperties {
		Objects.requireNonNull(cors, "gateway.cors.* is required");
		Objects.requireNonNull(limits, "gateway.limits.* is required");
		Objects.requireNonNull(rateLimit, "gateway.rate-limit.* is required");
	}

	/**
	 * @param allowedOrigins the SPA's origins for this environment (exact, no wildcard: credentials are allowed)
	 * @param maxAge how long a browser may cache a preflight answer
	 */
	public record Cors(List<String> allowedOrigins, Duration maxAge) {

		public Cors {
			Objects.requireNonNull(maxAge, "gateway.cors.max-age is required");
			allowedOrigins = allowedOrigins == null ? List.of()
					: allowedOrigins.stream().map(String::trim).filter(origin -> !origin.isEmpty()).toList();
			for (String origin : allowedOrigins) {
				if (origin.contains("*")) {
					throw new IllegalArgumentException(
							"gateway.cors.allowed-origins must list exact origins, not '" + origin + "'");
				}
			}
		}

	}

	/** @param maxBodySize the largest request body forwarded */
	public record Limits(DataSize maxBodySize) {

		public Limits {
			Objects.requireNonNull(maxBodySize, "gateway.limits.max-body-size is required");
		}

	}

	/**
	 * Requests per client IP in each window, counted by this gateway instance only.
	 *
	 * @param sensitive the sign-in, sign-up, refresh, password and seller-reapplication endpoints
	 * @param general every other request
	 */
	public record RateLimit(Tier sensitive, Tier general) {

		public RateLimit {
			Objects.requireNonNull(sensitive, "gateway.rate-limit.sensitive.* is required");
			Objects.requireNonNull(general, "gateway.rate-limit.general.* is required");
		}

	}

	public record Tier(int requests, Duration window) {

		public Tier {
			if (requests < 1 || window == null || window.isZero() || window.isNegative()) {
				throw new IllegalArgumentException("A rate-limit tier needs requests >= 1 and a positive window");
			}
		}

	}

}
