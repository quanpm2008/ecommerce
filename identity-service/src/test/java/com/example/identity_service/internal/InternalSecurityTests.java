package com.example.identity_service.internal;

import com.example.identity_service.EndpointTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Service credentials on {@code /internal/v1/identity} (API §7.1, checklist section 8): only a configured
 * {@code X-Service-Token} authenticates, and each endpoint admits only its listed consumers.
 */
class InternalSecurityTests extends EndpointTestSupport {

	@Test
	void callsWithoutAValidServiceTokenAreUnauthenticated() throws Exception {
		Session customer = customer();
		String contact = "/users/" + customer.userId() + "/contact";

		internal(HttpMethod.GET, contact, null, null, null).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		internal(HttpMethod.GET, contact, null, "not-a-configured-token-0123456789abcdef", null)
				.andExpect(status().isUnauthorized());
		internal(HttpMethod.GET, contact, null, "too-short", null).andExpect(status().isUnauthorized());
		// A user's valid access token is not a service credential.
		internal(HttpMethod.GET, contact, null, null, customer.accessToken()).andExpect(status().isUnauthorized());
		internal(HttpMethod.GET, "/customers/" + customer.userId() + "/checkout-eligibility", null, null, null)
				.andExpect(status().isUnauthorized());
	}

	@Test
	void eachEndpointAdmitsOnlyItsConsumers() throws Exception {
		Session customer = customer();
		String contact = "/users/" + customer.userId() + "/contact";
		String eligibility = "/customers/" + customer.userId() + "/checkout-eligibility";

		internal(HttpMethod.GET, contact, null, NOTIFICATION_TOKEN, null).andExpect(status().isOk());
		internal(HttpMethod.GET, contact, null, ORDER_TOKEN, null).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		internal(HttpMethod.GET, contact, null, GATEWAY_TOKEN, null).andExpect(status().isForbidden());
		internal(HttpMethod.GET, eligibility, null, ORDER_TOKEN, null).andExpect(status().isOk());
		internal(HttpMethod.GET, eligibility, null, NOTIFICATION_TOKEN, null).andExpect(status().isForbidden());
		internal(HttpMethod.GET, "/sellers/status?ids=" + UUID.randomUUID(), null, GATEWAY_TOKEN, null)
				.andExpect(status().isForbidden());
	}

	@Test
	void publicApiDoesNotAcceptServiceTokens() throws Exception {
		call(HttpMethod.GET, "/users/me", null, null).andExpect(status().isUnauthorized());
		mvc.perform(get(API + "/users/me").header("X-Service-Token", ORDER_TOKEN))
				.andExpect(status().isUnauthorized());
	}

}
