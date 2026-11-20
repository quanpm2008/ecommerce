package com.example.gateway_service.support;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

import com.example.gateway_service.support.StubService.Reply;

/**
 * Access tokens shaped like identity-service's (API §3.2: RS256, {@code kid}, {@code iss} {@code ecommerce-identity},
 * {@code aud} {@code ecommerce-api}, {@code sub}, {@code sessionId}, {@code authzVersion}, {@code iat}, {@code exp},
 * {@code jti}), signed with test keys whose public halves a {@link StubService} serves as the JWKS.
 */
public final class TestTokens {

	public static final String ISSUER = "ecommerce-identity";

	public static final String AUDIENCE = "ecommerce-api";

	/** The key the JWKS stub publishes by default. */
	public static final RSAKey KEY = newKey("test-key-a");

	private TestTokens() {
	}

	public static RSAKey newKey(String keyId) {
		try {
			return new RSAKeyGenerator(2048).keyID(keyId).generate();
		}
		catch (JOSEException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Starts a stub serving the public part of {@code keys} as a JWKS. */
	public static StubService startJwks(RSAKey... keys) {
		StubService jwks = StubService.start("identity-jwks");
		publish(jwks, keys);
		return jwks;
	}

	public static void publish(StubService jwks, RSAKey... keys) {
		List<JWK> publicKeys = Arrays.stream(keys).map(key -> (JWK) key.toPublicJWK()).toList();
		jwks.reply(Reply.json(200, new JWKSet(publicKeys).toString()));
	}

	public static String jwksUri(StubService jwks) {
		return jwks.uri() + "/.well-known/jwks.json";
	}

	/** Valid claims: issued now, expiring in 15 minutes. */
	public static JWTClaimsSet.Builder claims() {
		Instant now = Instant.now();
		return new JWTClaimsSet.Builder()
				.issuer(ISSUER)
				.audience(AUDIENCE)
				.subject(UUID.randomUUID().toString())
				.claim("sessionId", UUID.randomUUID().toString())
				.claim("authzVersion", 2)
				.issueTime(Date.from(now))
				.expirationTime(Date.from(now.plus(Duration.ofMinutes(15))))
				.jwtID(UUID.randomUUID().toString());
	}

	public static String valid() {
		return sign(KEY, claims().build());
	}

	public static String sign(RSAKey key, JWTClaimsSet claims) {
		return sign(key, JWSAlgorithm.RS256, claims);
	}

	public static String sign(RSAKey key, JWSAlgorithm algorithm, JWTClaimsSet claims) {
		try {
			SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID(key.getKeyID()).build(), claims);
			jwt.sign(new RSASSASigner(key));
			return jwt.serialize();
		}
		catch (JOSEException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** The classic algorithm-confusion attack: HS256 with the published RSA public key as the HMAC secret. */
	public static String hs256WithPublicKey(RSAKey key, JWTClaimsSet claims) {
		try {
			SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(key.getKeyID()).build(), claims);
			jwt.sign(new MACSigner(key.toRSAPublicKey().getEncoded()));
			return jwt.serialize();
		}
		catch (JOSEException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** {@code alg: none}. */
	public static String unsigned(JWTClaimsSet claims) {
		return new PlainJWT(claims).serialize();
	}

}
