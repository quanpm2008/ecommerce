package com.example.identity_service;

import com.example.identity_service.client.GoogleIdTokens;
import com.example.identity_service.exception.ApiException;
import com.example.identity_service.exception.ErrorCode;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Google for endpoint tests: {@link #issue} makes an ID token for an identity; any other token is rejected. */
public class FakeGoogleIdTokens implements GoogleIdTokens {

	private final Map<String, GoogleIdentity> issued = new ConcurrentHashMap<>();

	/** An ID token Google would issue for {@code subject}/{@code email}/{@code name}. */
	public String issue(String subject, String email, String name) {
		String token = "google-id-token-" + UUID.randomUUID();
		issued.put(token, new GoogleIdentity(subject, email, name));
		return token;
	}

	@Override
	public GoogleIdentity verify(String idToken) {
		GoogleIdentity identity = issued.get(idToken);
		if (identity == null) {
			throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
		}
		return identity;
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Configuration {

		@Bean
		@Primary
		FakeGoogleIdTokens fakeGoogleIdTokens() {
			return new FakeGoogleIdTokens();
		}

	}

}
