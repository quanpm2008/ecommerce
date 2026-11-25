package com.example.finance_service.security;

import com.example.finance_service.client.FinanceIdentityClient;
import com.example.finance_service.client.FinanceIdentityClient.Context;
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
public class FinanceAuthorization {
    public static final String READ = "wallet:read:own";
    public static final String WITHDRAW = "withdrawal:create:own";
    private final FinanceIdentityClient identity;
    private final NimbusJwtDecoder decoder;
    private final String credential;

    public FinanceAuthorization(FinanceIdentityClient identity,
            @Value("${finance.identity.base-url:http://localhost:8083}") String url,
            @Value("${finance.identity.service-token:}") String credential,
            @Value("${finance.identity.issuer:ecommerce-identity}") String issuer,
            @Value("${finance.identity.audience:ecommerce-api}") String audience) {
        this.identity = identity;
        this.credential = credential;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        decoder = NimbusJwtDecoder.withJwkSetUri(url.replaceFirst("/$", "") + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(new RestTemplate(factory)).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtIssuerValidator(issuer),
                new JwtTimestampValidator(Duration.ZERO), jwt -> {
                    if (jwt.getExpiresAt() == null || jwt.getAudience() == null || !jwt.getAudience().contains(audience)) {
                        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
                    }
                    return jwt.getExpiresAt().isAfter(Instant.now()) ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("TOKEN_EXPIRED"));
                }));
    }

    public SellerActor seller(String bearer, String permission) {
        var current = authenticate(bearer);
        var seller = current.sellerProfile();
        if (!"SELLER".equals(current.accountType()) || !current.roles().contains("SELLER") || seller == null
                || !"APPROVED".equals(seller.approvalStatus()) || !current.accountStatus().equals(seller.status())
                || !current.permissions().contains(permission)) {
            throw new Rejected(403, "FORBIDDEN");
        }
        boolean banned = "BANNED".equals(current.accountStatus());
        boolean full = !banned && "ACTIVE".equals(current.accountStatus()) && "FULL".equals(current.accessMode());
        boolean withdrawal = banned && ("WITHDRAWAL_ONLY".equals(current.accessMode())
                || "ORDERS_AND_WITHDRAWAL".equals(current.accessMode()));
        boolean readOnly = banned && ("OBLIGATIONS_READ_ONLY".equals(current.accessMode())
                || "OPEN_ORDERS_ONLY".equals(current.accessMode()));
        if (!(full || withdrawal || readOnly && READ.equals(permission))) {
            throw new Rejected(403, "FORBIDDEN");
        }
        return new SellerActor(current.userId(), banned, (full || withdrawal) && current.permissions().contains(WITHDRAW),
                "WITHDRAWAL_ONLY".equals(current.accessMode()), readOnly);
    }

    public void admin(String bearer) {
        var current = authenticate(bearer);
        if (!"ADMIN".equals(current.accountType()) || !"ACTIVE".equals(current.accountStatus())
                || !"FULL".equals(current.accessMode()) || !current.roles().contains("ADMIN")
                || !current.permissions().contains("operations:read:any")) {
            throw new Rejected(403, "FORBIDDEN");
        }
    }

    private Context authenticate(String bearer) {
        if (bearer == null || !bearer.startsWith("Bearer ") || bearer.length() <= 7) {
            throw new Rejected(401, "UNAUTHENTICATED");
        }
        UUID user;
        Jwt jwt;
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
        var rawVersion = jwt.getClaims().get("authzVersion");
        if (!(rawVersion instanceof Integer || rawVersion instanceof Long)) throw new Rejected(401, "UNAUTHENTICATED");
        if (credential.isBlank()) throw new Rejected(503, "SERVICE_UNAVAILABLE");
        Context current;
        try {
            var envelope = identity.context(credential, bearer);
            current = envelope == null ? null : envelope.data();
        } catch (FeignException failure) {
            if (failure.status() == 401) throw new Rejected(401, "UNAUTHENTICATED");
            if (failure.status() == 403) throw new Rejected(403, "FORBIDDEN");
            throw new Rejected(503, "SERVICE_UNAVAILABLE");
        }
        if (current == null || !user.equals(current.userId()) || current.authzVersion() == null
                || current.sessionActive() == null || current.accountStatus() == null || current.accessMode() == null
                || current.roles() == null || current.permissions() == null) throw new Rejected(503, "SERVICE_UNAVAILABLE");
        long version = ((Number) rawVersion).longValue();
        if (version < current.authzVersion()) throw new Rejected(401, "TOKEN_STALE");
        if (version != current.authzVersion() || !current.sessionActive()) throw new Rejected(401, "UNAUTHENTICATED");
        if (!"ACTIVE".equals(current.userStatus()) || "DISABLED".equals(current.accountStatus())) {
            throw new Rejected(403, "ACCOUNT_DISABLED");
        }
        return current;
    }

    public record SellerActor(UUID id, boolean banned, boolean canWithdraw, boolean withdrawalOnly, boolean readOnly) {}
    public static final class Rejected extends RuntimeException {
        private final int status;
        public Rejected(int status, String code) {
            super(code);
            this.status = status;
        }
        public int status() { return status; }
    }
}
