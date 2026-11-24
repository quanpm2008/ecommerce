package com.example.order_service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class OrderAuthorization {
	static final String OPERATIONS_READ = "operations:read:any";
	static final String OPERATIONS_ANNOTATE = "operations:annotate";

	static final String CHECKOUT = "checkout:create:own";
	static final String CHECKOUT_READ = "order:read:own";
	static final String ORDER_CANCEL = "order:cancel:own";
	static final String ORDER_CONFIRM = "order:confirm:own";
	static final String FULFILL = "order:fulfill:own";
	static final String INVENTORY = "inventory:manage:own";
	private static final String UNAUTHENTICATED = "UNAUTHENTICATED";
	private static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
	private static final String TOKEN_STALE = "TOKEN_STALE";

	private final NimbusJwtDecoder decoder;
	private final RestClient identity;
	private final String serviceToken;

	OrderAuthorization(
			@Value("${order.identity.base-url:http://localhost:8083}") String baseUrl,
			@Value("${order.identity.issuer:ecommerce-identity}") String issuer,
			@Value("${order.identity.audience:ecommerce-api}") String audience,
			@Value("${order.identity.service-token:}") String serviceToken) {
		var requests = internalRequests();
		identity = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
		decoder = NimbusJwtDecoder.withJwkSetUri(baseUrl + (baseUrl.endsWith("/") ? "" : "/") + ".well-known/jwks.json")
				.jwsAlgorithm(SignatureAlgorithm.RS256)
				.restOperations(new RestTemplate(requests))
				.build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtIssuerValidator(issuer),
				new JwtTimestampValidator(Duration.ZERO), jwt -> {
					Instant expiry = jwt.getExpiresAt();
					if (expiry == null || jwt.getAudience() == null || !jwt.getAudience().contains(audience)) {
						return OAuth2TokenValidatorResult.failure(new OAuth2Error(UNAUTHENTICATED));
					}
					return !expiry.isAfter(Instant.now())
							? OAuth2TokenValidatorResult.failure(new OAuth2Error(TOKEN_EXPIRED))
							: OAuth2TokenValidatorResult.success();
				}));
		this.serviceToken = serviceToken;
	}

	private static JdkClientHttpRequestFactory internalRequests() {
		var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
		var requests = new JdkClientHttpRequestFactory(client);
		requests.setReadTimeout(Duration.ofSeconds(2));
		return requests;
	}

	CurrentUser authorize(String token, String permission) {
		return authorize(token, permission, false, false);
	}

	CurrentUser authorize(String token, String permission, boolean lifecycle, boolean detail) {
		Jwt jwt = verifiedToken(token);
		UUID user = uuidClaim(jwt.getSubject());
		UUID session = uuidClaim(jwt.getClaimAsString("sessionId"));
		Object rawVersion = jwt.getClaims().get("authzVersion");
		if (!(rawVersion instanceof Integer || rawVersion instanceof Long)) {
			throw new Rejected(401, UNAUTHENTICATED);
		}
		long version = ((Number) rawVersion).longValue();
		IdentityContext current = currentContext(token);
		if (!user.equals(current.userId())) {
			throw unavailable();
		}
		if (version < current.authzVersion()) {
			throw new Rejected(401, TOKEN_STALE);
		}
		if (version != current.authzVersion() || !current.sessionActive()) {
			throw new Rejected(401, UNAUTHENTICATED);
		}
		if ("DISABLED".equals(current.userStatus()) || "DISABLED".equals(current.accountStatus())) {
			throw new Rejected(403, "ACCOUNT_DISABLED");
		}
		if ("BANNED".equals(current.accountStatus())) {
			if (lifecycle && limitedAllows(current, permission, detail)) {
				return user(user, session, version, current, true);
			}
			throw new Rejected(403, "ACCOUNT_BANNED");
		}
		if (!"ACTIVE".equals(current.userStatus()) || !"ACTIVE".equals(current.accountStatus())
				|| !"FULL".equals(current.accessMode()) || !current.permissions().contains(permission)
				|| !(lifecycle ? profileAllowsLifecycle(current, permission, false) : profileAllows(current, permission))) {
			throw new Rejected(403, "FORBIDDEN");
		}
		return user(user, session, version, current, false);
	}

	private static CurrentUser user(UUID user, UUID session, long version, IdentityContext current, boolean limited) {
		return new CurrentUser(user, session, version, limited, "OBLIGATIONS_READ_ONLY".equals(current.accessMode()),
				List.copyOf(current.permissions()));
	}

	private static boolean limitedAllows(IdentityContext current, String permission, boolean detail) {
		if (!"ACTIVE".equals(current.userStatus()) || !current.permissions().contains(permission)
				|| !profileAllowsLifecycle(current, permission, true)) {
			return false;
		}
		return switch (current.accessMode()) {
			case "OPEN_ORDERS_ONLY" -> true;
			case "ORDERS_AND_WITHDRAWAL" -> FULFILL.equals(permission);
			case "OBLIGATIONS_READ_ONLY" -> detail && CHECKOUT_READ.equals(permission);
			default -> false;
		};
	}

	private static boolean profileAllowsLifecycle(IdentityContext current, String permission, boolean limited) {
		String status = limited ? "BANNED" : "ACTIVE";
		if (FULFILL.equals(permission)) {
			return "SELLER".equals(current.accountType()) && current.roles().contains("SELLER")
					&& current.sellerProfile() != null && "APPROVED".equals(current.sellerProfile().approvalStatus())
					&& status.equals(current.sellerProfile().status());
		}
		return "CUSTOMER".equals(current.accountType()) && current.roles().contains("CUSTOMER")
				&& current.customerProfile() != null && status.equals(current.customerProfile().status());
	}

	private static boolean profileAllows(IdentityContext current, String permission) {
		if (OPERATIONS_READ.equals(permission) || OPERATIONS_ANNOTATE.equals(permission)) {
			return "ADMIN".equals(current.accountType()) && current.roles().contains("ADMIN");
		}
		if ((CHECKOUT.equals(permission) || CHECKOUT_READ.equals(permission))
				&& "CUSTOMER".equals(current.accountType()) && current.roles().contains("CUSTOMER")) {
			if (current.customerProfile() == null || current.customerProfile().status() == null) {
				throw unavailable();
			}
			return "ACTIVE".equals(current.customerProfile().status());
		}
		if (INVENTORY.equals(permission) && "SELLER".equals(current.accountType()) && current.roles().contains("SELLER")) {
			if (current.sellerProfile() == null || current.sellerProfile().status() == null
					|| current.sellerProfile().approvalStatus() == null) {
				throw unavailable();
			}
			return "ACTIVE".equals(current.sellerProfile().status())
					&& "APPROVED".equals(current.sellerProfile().approvalStatus());
		}
		return false;
	}

	public CurrentUser authorizeAdmin(String token, boolean annotate) {
		return authorize(token, annotate ? OPERATIONS_ANNOTATE : OPERATIONS_READ);
	}

	private Jwt verifiedToken(String token) {
		if (token == null || token.isBlank()) {
			throw new Rejected(401, UNAUTHENTICATED);
		}
		try {
			return decoder.decode(token);
		} catch (JwtValidationException invalid) {
			boolean expired = invalid.getErrors().stream().anyMatch(error -> TOKEN_EXPIRED.equals(error.getErrorCode()));
			throw new Rejected(401, expired ? TOKEN_EXPIRED : UNAUTHENTICATED);
		} catch (BadJwtException invalid) {
			throw new Rejected(401, UNAUTHENTICATED);
		} catch (JwtException unavailable) {
			throw unavailable();
		}
	}

	private IdentityContext currentContext(String token) {
		if (serviceToken.isBlank()) {
			throw unavailable();
		}
		try {
			var body = identity.post().uri("/internal/v1/identity/auth/authorization-context")
					.header("X-Service-Token", serviceToken).headers(headers -> headers.setBearerAuth(token))
					.retrieve().body(ContextResponse.class);
			IdentityContext current = body == null ? null : body.data();
			if (current == null || current.userId() == null || current.accountType() == null
					|| current.accountStatus() == null || current.userStatus() == null || current.accessMode() == null
					|| current.authzVersion() == null || current.sessionActive() == null || current.roles() == null
					|| current.permissions() == null) {
				throw unavailable();
			}
			return current;
		} catch (RestClientResponseException upstream) {
			throw knownIdentityFailure(upstream);
		} catch (RestClientException upstream) {
			throw unavailable();
		}
	}

	private static Rejected knownIdentityFailure(RestClientResponseException upstream) {
		if (upstream.getStatusCode().value() == 401) {
			try {
				var envelope = upstream.getResponseBodyAs(ErrorResponse.class);
				String code = envelope == null || envelope.error() == null ? null : envelope.error().code();
				if (TOKEN_STALE.equals(code) || TOKEN_EXPIRED.equals(code) || UNAUTHENTICATED.equals(code)) {
					return new Rejected(401, code);
				}
			} catch (RestClientException | IllegalStateException malformed) {
				return unavailable();
			}
		}
		return unavailable();
	}

	private static UUID uuidClaim(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException | NullPointerException invalid) {
			throw new Rejected(401, UNAUTHENTICATED);
		}
	}

	private static Rejected unavailable() {
		return new Rejected(503, "SERVICE_UNAVAILABLE");
	}

	public record CurrentUser(UUID userId, UUID sessionId, long authzVersion, boolean limited, boolean readOnly,
			List<String> permissions) {
	}

	private record CustomerProfile(String status) {
	}

	private record SellerProfile(String approvalStatus, String status) {
	}

	private record IdentityContext(UUID userId, String accountType, String accountStatus, String userStatus,
			String accessMode, Long authzVersion, List<String> roles, List<String> permissions,
			CustomerProfile customerProfile, SellerProfile sellerProfile, Boolean sessionActive) {
	}

	private record ContextResponse(IdentityContext data) {
	}

	private record ErrorResponse(ErrorCode error) {
	}

	private record ErrorCode(String code) {
	}

	public static final class Rejected extends RuntimeException {
		private final int status;

		Rejected(int status, String code) {
			super(code);
			this.status = status;
		}

		public int status() {
			return status;
		}
	}
}
