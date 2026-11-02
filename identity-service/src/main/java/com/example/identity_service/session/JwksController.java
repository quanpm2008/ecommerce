package com.example.identity_service.session;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public signing keys for the gateway and other services (RFC 7517). Deliberately not wrapped in the {@code data}
 * envelope: JWT libraries read this format directly. The gateway does not route it; services call it internally.
 */
@Tag(name = "Keys", description = "Token verification keys for other services")
@RestController
@RequiredArgsConstructor
public class JwksController {

	public static final String PATH = "/.well-known/jwks.json";

	private final RSAKey signingKey;

	@Operation(summary = "Public signing keys (JWKS)", description = "RFC 7517 key set, not wrapped in `data`. Verify "
			+ "access tokens with it: RS256, `iss` ecommerce-identity, `aud` ecommerce-api. Not routed by the gateway.")
	@GetMapping(PATH)
	Map<String, Object> jwks() {
		return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
	}

}
