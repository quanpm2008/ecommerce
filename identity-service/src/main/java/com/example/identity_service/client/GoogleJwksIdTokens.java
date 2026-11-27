package com.example.identity_service.client;

import com.example.identity_service.config.GoogleProperties;
import com.example.identity_service.exception.ApiException;
import com.example.identity_service.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Verifies Google ID tokens locally against Google's published keys (RS256), as Google recommends: signature,
 * {@code exp}, {@code iss} ∈ {@code identity.google.issuers}, {@code aud} contains {@code identity.google.client-id},
 * then {@code email_verified} (UC-A01.ALT-05). A token that fails any check is {@code GOOGLE_AUTH_FAILED}; keys that
 * cannot be fetched are {@code SERVICE_UNAVAILABLE}, because the token was not judged at all.
 */
@Slf4j
@Component
public class GoogleJwksIdTokens implements GoogleIdTokens {

	private final GoogleProperties properties;

	private final JwtDecoder decoder;

	@Autowired
	public GoogleJwksIdTokens(GoogleProperties properties, Clock clock) {
		this(properties, clock, NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build());
	}

	/** {@code keys} decodes and verifies the signature; the claim checks are added here. */
	GoogleJwksIdTokens(GoogleProperties properties, Clock clock, NimbusJwtDecoder keys) {
		this.properties = properties;
		keys.setJwtValidator(validator(properties, clock));
		this.decoder = keys;
	}

	@Override
	public GoogleIdentity verify(String idToken) {
		if (!properties.enabled()) {
			throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Google sign-in is not configured");
		}
		Jwt jwt;
		try {
			jwt = decoder.decode(idToken);
		}
		catch (BadJwtException rejected) {
			log.debug("Google ID token rejected: {}", rejected.getMessage());
			throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
		}
		catch (JwtException unavailable) {
			log.warn("Google keys unavailable: {}", unavailable.getMessage());
			throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
		}
		String email = jwt.getClaimAsString("email");
		if (email == null || email.isBlank() || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
				|| jwt.getSubject() == null || jwt.getSubject().isBlank()) {
			throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
		}
		return new GoogleIdentity(jwt.getSubject(), email, jwt.getClaimAsString("name"));
	}

	private static OAuth2TokenValidator<Jwt> validator(GoogleProperties properties, Clock clock) {
		// Google issues both "accounts.google.com" and "https://accounts.google.com"; the first is not a URL.
		OAuth2TokenValidator<Jwt> issuer = jwt -> properties.issuers().contains(jwt.getClaimAsString("iss"))
				? OAuth2TokenValidatorResult.success() : failure("issuer");
		OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null
				&& jwt.getAudience().contains(properties.clientId()) ? OAuth2TokenValidatorResult.success()
						: failure("audience");
		JwtTimestampValidator timestamps = new JwtTimestampValidator();
		timestamps.setClock(clock);
		return new DelegatingOAuth2TokenValidator<>(timestamps, issuer, audience);
	}

	private static OAuth2TokenValidatorResult failure(String claim) {
		return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong " + claim, null));
	}

}
