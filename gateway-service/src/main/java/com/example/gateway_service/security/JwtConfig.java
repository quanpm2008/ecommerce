package com.example.gateway_service.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.example.gateway_service.error.ErrorCode;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

/**
 * Access token checks at the gateway (API §2.3): RS256 only, signature against identity-service's JWKS, {@code exp}
 * (with {@code gateway.jwt.clock-skew}), {@code iss}, {@code aud}, and a non-blank {@code sub} and {@code sessionId}.
 * <p>
 * The JWKS is fetched on the first token, not at startup, so the gateway runs while identity-service is still starting;
 * it is cached, and fetched again when a token names a key id it doesn't know (identity-service's key id is the key's
 * thumbprint, so a new key means a new id). Whether the session is still active and what the user may do is not
 * decided here: every service asks Identity for the authorization context.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
class JwtConfig {

	static final String SESSION_ID = "sessionId";

	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties) {
		SimpleClientHttpRequestFactory http = new SimpleClientHttpRequestFactory();
		http.setConnectTimeout(properties.jwksTimeout());
		http.setReadTimeout(properties.jwksTimeout());
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri().toString())
				.jwsAlgorithm(SignatureAlgorithm.RS256)
				.restOperations(new RestTemplate(http))
				.build();
		decoder.setJwtValidator(validator(properties, Clock.systemUTC()));
		return decoder;
	}

	static OAuth2TokenValidator<Jwt> validator(JwtProperties properties, Clock clock) {
		return new DelegatingOAuth2TokenValidator<>(List.of(
				new Lifetime(properties.clockSkew(), clock),
				new JwtIssuerValidator(properties.issuer()),
				new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
						aud -> aud != null && aud.contains(properties.audience())),
				new JwtClaimValidator<Object>(JwtClaimNames.SUB, JwtConfig::isNonBlankString),
				new JwtClaimValidator<Object>(SESSION_ID, JwtConfig::isNonBlankString)));
	}

	private static boolean isNonBlankString(Object value) {
		return value instanceof String text && !text.isBlank();
	}

	/**
	 * {@code exp} is required and an expired token fails with the {@code TOKEN_EXPIRED} code (so the client knows to
	 * refresh); a future {@code nbf} is just invalid. Replaces Spring's timestamp validator, which reports both alike.
	 */
	record Lifetime(Duration skew, Clock clock) implements OAuth2TokenValidator<Jwt> {

		@Override
		public OAuth2TokenValidatorResult validate(Jwt jwt) {
			Instant now = clock.instant();
			Instant expiresAt = jwt.getExpiresAt();
			if (expiresAt == null) {
				return invalid("The token has no exp claim");
			}
			if (now.isAfter(expiresAt.plus(skew))) {
				return OAuth2TokenValidatorResult.failure(
						new OAuth2Error(ErrorCode.TOKEN_EXPIRED.name(), "The token has expired", null));
			}
			Instant notBefore = jwt.getNotBefore();
			if (notBefore != null && now.plus(skew).isBefore(notBefore)) {
				return invalid("The token is not valid yet");
			}
			return OAuth2TokenValidatorResult.success();
		}

		private static OAuth2TokenValidatorResult invalid(String description) {
			return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
		}

	}

}
