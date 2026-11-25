package com.example.payment_service.security;

import com.example.payment_service.client.PaymentIdentityClient;
import feign.FeignException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
import org.springframework.web.client.RestTemplate;

@Component
public class RefundCustomerAuthorization {
    public static final String READ = "order:read:own";
    public static final String RETRY = "refund:retry:own";
    private final PaymentIdentityClient identity;
    private final String credential;
    private final NimbusJwtDecoder decoder;

    public RefundCustomerAuthorization(PaymentIdentityClient identity,
            @Value("${payment.identity.base-url:http://localhost:8083}") String url,
            @Value("${payment.identity.service-token:}") String credential,
            @Value("${payment.identity.issuer:ecommerce-identity}") String issuer,
            @Value("${payment.identity.audience:ecommerce-api}") String audience) {
        this.identity = identity;
        this.credential = credential;
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(2));
        requests.setReadTimeout(Duration.ofSeconds(2));
        decoder = NimbusJwtDecoder.withJwkSetUri(url.replaceFirst("/$", "") + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(new RestTemplate(requests)).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtIssuerValidator(issuer),
                new JwtTimestampValidator(Duration.ZERO), jwt -> {
                    if (jwt.getExpiresAt() == null || jwt.getAudience() == null || !jwt.getAudience().contains(audience)) {
                        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
                    }
                    return jwt.getExpiresAt().isAfter(Instant.now()) ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("TOKEN_EXPIRED"));
                }));
    }

    public Actor authorize(String bearer, String permission) {
        var current = authenticate(bearer);
        if (!"CUSTOMER".equals(current.accountType()) || !current.roles().contains("CUSTOMER")) {
            throw new Rejected(403, "FORBIDDEN");
        }
        boolean banned = "BANNED".equals(current.accountStatus());
        boolean full = "ACTIVE".equals(current.accountStatus()) && "FULL".equals(current.accessMode());
        boolean openOrders = banned && "OPEN_ORDERS_ONLY".equals(current.accessMode());
        boolean readOnly = banned && "OBLIGATIONS_READ_ONLY".equals(current.accessMode());
        if (!"ACTIVE".equals(current.userStatus()) || current.customerProfile() == null
                || !current.accountStatus().equals(current.customerProfile().status())
                || !current.permissions().contains(permission)
                || !(full || openOrders || (readOnly && READ.equals(permission)))) {
            throw new Rejected(403, banned ? "ACCOUNT_BANNED" : "FORBIDDEN");
        }
        return new Actor(current.userId(), banned, (full || openOrders) && current.permissions().contains(RETRY));
    }

    public void authorizeAdmin(String bearer, String permission) {
        var current = authenticate(bearer);
        if (!"ACTIVE".equals(current.userStatus()) || !"ACTIVE".equals(current.accountStatus())
                || !"FULL".equals(current.accessMode()) || !"ADMIN".equals(current.accountType())
                || !current.roles().contains("ADMIN") || !current.permissions().contains(permission)) {
            throw new Rejected(403, "FORBIDDEN");
        }
    }

    private PaymentIdentityClient.Context authenticate(String bearer) {
        if (bearer == null || !bearer.startsWith("Bearer ") || bearer.length() <= 7) {
            throw new Rejected(401, "UNAUTHENTICATED");
        }
        Jwt jwt;
        UUID user;
        try {
            jwt = decoder.decode(bearer.substring(7));
            user = UUID.fromString(jwt.getSubject());
            UUID.fromString(jwt.getClaimAsString("sessionId"));
        } catch (JwtValidationException invalid) {
            boolean expired = invalid.getErrors().stream().anyMatch(error -> "TOKEN_EXPIRED".equals(error.getErrorCode()));
            throw new Rejected(401, expired ? "TOKEN_EXPIRED" : "UNAUTHENTICATED");
        } catch (BadJwtException | IllegalArgumentException | NullPointerException invalid) {
            throw new Rejected(401, "UNAUTHENTICATED");
        } catch (JwtException unavailable) {
            throw new Rejected(503, "SERVICE_UNAVAILABLE");
        }
        var version = jwt.getClaims().get("authzVersion");
        if (!(version instanceof Integer || version instanceof Long)) {
            throw new Rejected(401, "UNAUTHENTICATED");
        }
        var current = current(bearer);
        if (current == null || !user.equals(current.userId()) || current.authzVersion() == null
                || current.sessionActive() == null || current.accountStatus() == null
                || current.accessMode() == null || current.roles() == null || current.permissions() == null) {
            throw new Rejected(503, "SERVICE_UNAVAILABLE");
        }
        long knownVersion = ((Number) version).longValue();
        if (knownVersion < current.authzVersion()) {
            throw new Rejected(401, "TOKEN_STALE");
        }
        if (knownVersion != current.authzVersion() || !current.sessionActive()) {
            throw new Rejected(401, "UNAUTHENTICATED");
        }
        if ("DISABLED".equals(current.accountStatus()) || "DISABLED".equals(current.userStatus())) {
            throw new Rejected(403, "ACCOUNT_DISABLED");
        }
        return current;
    }

    private PaymentIdentityClient.Context current(String bearer) {
        if (credential.isBlank()) {
            throw new Rejected(503, "SERVICE_UNAVAILABLE");
        }
        try {
            var reply = identity.context(credential, bearer);
            return reply == null ? null : reply.data();
        } catch (FeignException failure) {
            throw failure.status() == 401 ? new Rejected(401, "UNAUTHENTICATED") : new Rejected(503, "SERVICE_UNAVAILABLE");
        }
    }

    public record Actor(UUID userId, boolean limited, boolean canRetry) {}

    public static final class Rejected extends RuntimeException {
        private final int status;
        public Rejected(int status, String code) {
            super(code);
            this.status = status;
        }
        public int status() { return status; }
    }
}
