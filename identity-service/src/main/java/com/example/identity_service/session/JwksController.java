package com.example.identity_service.session;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public signing keys for the gateway and other services (RFC 7517). Deliberately not wrapped in the {@code data}
 * envelope: JWT libraries read this format directly. The gateway does not route it; services call it internally.
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

	public static final String PATH = "/.well-known/jwks.json";

	private final RSAKey signingKey;

	@GetMapping(PATH)
	Map<String, Object> jwks() {
		return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
	}

}
