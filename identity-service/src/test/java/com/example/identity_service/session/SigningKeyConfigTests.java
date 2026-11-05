package com.example.identity_service.session;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SigningKeyConfigTests {

	@Test
	void configuredPemKeyIsUsedAndItsIdIsStable() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair pair = generator.generateKeyPair();
		String pem = "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPrivate().getEncoded())
				+ "\n-----END PRIVATE KEY-----\n";

		RSAKey first = new SigningKeyConfig().signingKey(properties(pem));
		RSAKey second = new SigningKeyConfig().signingKey(properties(pem));

		assertThat(first.toRSAPublicKey()).isEqualTo(pair.getPublic());
		assertThat(first.isPrivate()).isTrue();
		assertThat(first.getKeyID()).isNotBlank().isEqualTo(second.getKeyID());
	}

	@Test
	void withoutAConfiguredKeyEachStartupGetsANewOne() throws Exception {
		RSAKey first = new SigningKeyConfig().signingKey(properties(""));
		RSAKey second = new SigningKeyConfig().signingKey(properties(""));

		assertThat(first.getKeyID()).isNotEqualTo(second.getKeyID());
	}

	private static TokenProperties properties(String signingKey) {
		return new TokenProperties("iss", "aud", Duration.ofMinutes(15), Duration.ofDays(7), signingKey);
	}

}
