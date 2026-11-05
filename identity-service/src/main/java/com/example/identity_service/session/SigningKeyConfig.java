package com.example.identity_service.session;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * The RS256 key that signs access tokens. Only its public half leaves the service, through the JWKS endpoint; the
 * key id is the JWK thumbprint, so verifiers pick up a new key after a rotation or restart.
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
public class SigningKeyConfig {

	@Bean
	RSAKey signingKey(TokenProperties properties) throws GeneralSecurityException, JOSEException {
		RSAPrivateCrtKey privateKey;
		if (StringUtils.hasText(properties.signingKey())) {
			privateKey = parsePkcs8(properties.signingKey());
		}
		else {
			log.warn("identity.token.signing-key is not set: using a temporary RSA key. Tokens stop working after a "
					+ "restart and differ between instances; configure a key outside local development.");
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			privateKey = (RSAPrivateCrtKey) generator.generateKeyPair().getPrivate();
		}
		RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
				.generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
		return new RSAKey.Builder(publicKey).privateKey(privateKey).keyIDFromThumbprint().build();
	}

	@Bean
	JwtEncoder jwtEncoder(RSAKey signingKey) {
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
	}

	private static RSAPrivateCrtKey parsePkcs8(String pem) throws GeneralSecurityException {
		String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
		return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
				.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
	}

}
