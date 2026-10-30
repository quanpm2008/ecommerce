package com.example.identity_service.session;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Clock;
import java.util.List;

/**
 * How Identity verifies access tokens on its own protected endpoints: RS256 only, with its own key, then
 * {@code exp}, {@code iss}, {@code aud} and the database-backed {@link SessionTokenValidator}. Other services do the
 * signature, {@code iss}, {@code aud} and {@code exp} checks with the JWKS endpoint and ask Identity for the rest.
 */
@Configuration(proxyBeanMethods = false)
public class AccessTokenDecoderConfig {

	public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";

	@Bean
	JwtDecoder jwtDecoder(RSAKey signingKey, TokenProperties properties, SessionTokenValidator sessionValidator,
			Clock clock) throws JOSEException {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey())
				.signatureAlgorithm(SignatureAlgorithm.RS256)
				.build();
		JwtTimestampValidator timestamps = new JwtTimestampValidator();
		timestamps.setClock(clock);
		OAuth2TokenValidator<Jwt> expiry = jwt -> timestamps.validate(jwt).hasErrors()
				? OAuth2TokenValidatorResult.failure(new OAuth2Error(TOKEN_EXPIRED, "Token has expired", null))
				: OAuth2TokenValidatorResult.success();
		OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
				aud -> aud != null && aud.contains(properties.audience()));
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(expiry,
				new JwtIssuerValidator(properties.issuer()), audience, sessionValidator));
		return decoder;
	}

}
