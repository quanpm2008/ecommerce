package com.example.gateway_service.security;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the gateway checks access tokens issued by identity-service ({@code gateway.jwt.*}, API §2.3, §3.2).
 *
 * @param issuer expected {@code iss}
 * @param audience value that {@code aud} must contain
 * @param jwkSetUri identity-service's JWKS ({@code /.well-known/jwks.json}); in Docker a service URL, not localhost
 * @param clockSkew tolerance on {@code exp} and {@code nbf} for clock differences between machines
 * @param jwksTimeout connect and read timeout when fetching the JWKS
 */
@ConfigurationProperties("gateway.jwt")
public record JwtProperties(String issuer, String audience, URI jwkSetUri, Duration clockSkew, Duration jwksTimeout) {

	public JwtProperties {
		Objects.requireNonNull(issuer, "gateway.jwt.issuer is required");
		Objects.requireNonNull(audience, "gateway.jwt.audience is required");
		Objects.requireNonNull(jwkSetUri, "gateway.jwt.jwk-set-uri is required");
		Objects.requireNonNull(clockSkew, "gateway.jwt.clock-skew is required");
		Objects.requireNonNull(jwksTimeout, "gateway.jwt.jwks-timeout is required");
	}

}
