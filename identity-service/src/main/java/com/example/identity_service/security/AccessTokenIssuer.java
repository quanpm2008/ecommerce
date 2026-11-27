package com.example.identity_service.security;

import com.example.identity_service.config.TokenProperties;
import com.example.identity_service.service.PermissionResolver;
import com.example.identity_service.entity.User;
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
 * Signs access tokens with the identifiers services need (API §3.2): {@code sub}, {@code sessionId},
 * {@code authzVersion}, plus the account type and a snapshot of its one role for the UI. No email, phone or
 * permissions. Services never trust the snapshot: they ask Identity for the current permissions using {@code sub},
 * {@code sessionId} and {@code authzVersion}.
 */
@Component
@RequiredArgsConstructor
public class AccessTokenIssuer {

	private final JwtEncoder encoder;

	private final TokenProperties properties;

	private final Clock clock;

	private final PermissionResolver permissions;

	public String issue(User user, UUID sessionId) {
		Instant now = clock.instant();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.audience(List.of(properties.audience()))
				.subject(user.getUserId().toString())
				.issuedAt(now)
				.expiresAt(now.plus(properties.accessTtl()))
				.id(UUID.randomUUID().toString())
				.claim(TokenClaims.SESSION_ID, sessionId.toString())
				.claim(TokenClaims.AUTHZ_VERSION, user.getAuthzVersion())
				.claim(TokenClaims.ACCOUNT_TYPE, user.getAccountType().name());
		permissions.activeRolesOf(user).stream().findFirst()
				.ifPresent(role -> claims.claim(TokenClaims.ROLE, role.name()));
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}

}
