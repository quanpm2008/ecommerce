package com.example.identity_service.client;

import com.example.identity_service.config.GoogleProperties;
import com.example.identity_service.exception.ApiException;
import com.example.identity_service.exception.ErrorCode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The checks on a Google ID token (signature, exp, iss, aud, email_verified), with tokens signed by a local key. */
class GoogleJwksIdTokensTests {

	private static final String CLIENT_ID = "web-client.apps.googleusercontent.com";

	private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");

	private static final GoogleProperties PROPERTIES = new GoogleProperties(CLIENT_ID, "http://unused",
			List.of("accounts.google.com", "https://accounts.google.com"));

	private final KeyPair google = rsa();

	private final GoogleJwksIdTokens tokens = verifier(PROPERTIES, google);

	@Test
	void aValidTokenGivesTheVerifiedIdentity() {
		var identity = tokens.verify(sign(google, claims -> claims));

		assertThat(identity).isEqualTo(new GoogleIdTokens.GoogleIdentity("1234567890", "an@gmail.com", "Nguyen An"));
		assertThat(tokens.verify(sign(google, claims -> claims.issuer("accounts.google.com"))).subject())
				.isEqualTo("1234567890");
	}

	@Test
	void anyFailedCheckIsGoogleAuthFailed() {
		List<String> rejected = List.of(
				sign(rsa(), claims -> claims),
				sign(google, claims -> claims.audience("another-app")),
				sign(google, claims -> claims.issuer("https://evil.example")),
				sign(google, claims -> claims.expirationTime(Date.from(NOW.minus(Duration.ofMinutes(5))))),
				sign(google, claims -> claims.claim("email_verified", false)),
				sign(google, claims -> claims.claim("email", null)),
				"not.a.jwt");

		for (String token : rejected) {
			assertThatThrownBy(() -> tokens.verify(token)).isInstanceOfSatisfying(ApiException.class,
					ex -> assertThat(ex.code()).isEqualTo(ErrorCode.GOOGLE_AUTH_FAILED));
		}
	}

	@Test
	void notConfiguredOrKeysUnreachableIsServiceUnavailable() {
		GoogleProperties off = new GoogleProperties("", "http://unused", PROPERTIES.issuers());
		GoogleProperties unreachable = new GoogleProperties(CLIENT_ID, "http://127.0.0.1:9/certs",
				PROPERTIES.issuers());
		String token = sign(google, claims -> claims);

		assertThatThrownBy(() -> verifier(off, google).verify(token)).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
		assertThatThrownBy(() -> new GoogleJwksIdTokens(unreachable, Clock.fixed(NOW, ZoneOffset.UTC)).verify(token))
				.isInstanceOfSatisfying(ApiException.class,
						ex -> assertThat(ex.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
	}

	private static GoogleJwksIdTokens verifier(GoogleProperties properties, KeyPair keys) {
		return new GoogleJwksIdTokens(properties, Clock.fixed(NOW, ZoneOffset.UTC),
				NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());
	}

	private static String sign(KeyPair keys, UnaryOperator<JWTClaimsSet.Builder> change) {
		JWTClaimsSet claims = change.apply(new JWTClaimsSet.Builder().issuer("https://accounts.google.com")
				.audience(CLIENT_ID).subject("1234567890").claim("email", "an@gmail.com")
				.claim("email_verified", true).claim("name", "Nguyen An").issueTime(Date.from(NOW))
				.expirationTime(Date.from(NOW.plus(Duration.ofHours(1))))).build();
		try {
			SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
			jwt.sign(new RSASSASigner(keys.getPrivate()));
			return jwt.serialize();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static KeyPair rsa() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
