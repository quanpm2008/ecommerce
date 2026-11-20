package com.example.cart_service.client;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** What Cart needs from Identity. Implementations throw {@code ApiException} (401 or 503), never guess. */
public interface IdentityGateway {

	/** Verifies the access token at Identity and returns the current context; 401 codes are passed on. */
	AuthorizationContext authorizationContext(String accessToken);

	/** G-06 per seller. A seller Identity does not return is not sellable. Source failure is 503. */
	Map<UUID, Boolean> sellable(Collection<UUID> sellerIds);

	/** Shop names for display only; a seller whose name cannot be read is simply absent. */
	Map<UUID, String> sellerNames(Collection<UUID> sellerIds);

}
