package com.example.identity_service.session;

import com.example.identity_service.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Signs access tokens with only the identifiers services need; no email, phone, roles or permissions. Services ask
 * Identity for the current permissions using {@code sub}, {@code sessionId} and {@code authzVersion}.
 */
@Component
@RequiredArgsConstructor
class AccessTokenIssuer {

	private final JwtEncoder encoder;

	private final TokenProperties properties;

	private final Clock clock;

	String issue(User user, UUID sessionId) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.audience(List.of(properties.audience()))
				.subject(user.getUserId().toString())
				.issuedAt(now)
				.expiresAt(now.plus(properties.accessTtl()))
				.id(UUID.randomUUID().toString())
				.claim(TokenClaims.SESSION_ID, sessionId.toString())
				.claim(TokenClaims.AUTHZ_VERSION, user.getAuthzVersion())
				.build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
