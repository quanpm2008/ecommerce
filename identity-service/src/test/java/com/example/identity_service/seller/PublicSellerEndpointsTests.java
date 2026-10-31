package com.example.identity_service.seller;

import com.example.identity_service.EndpointTestSupport;
import com.example.identity_service.rbac.RoleCode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /sellers} and {@code GET /sellers/{sellerId}} (API §6.1, UC-B02, G-06, checklist section 6). Every test
 * names its shops with a fresh tag and searches for it, so sellers from other tests never show up.
 */
class PublicSellerEndpointsTests extends EndpointTestSupport {

	@Test
	void onlyVisibleSellersAreListedAndFound() throws Exception {
		String tag = tag();
		UUID visible = seller("Visible " + tag, true);
		UUID pending = seller("Pending " + tag, false);
		UUID rejected = seller("Rejected " + tag, false);
		jdbc.update("""
				update sellers set approval_status = 'REJECTED', rejection_reason = 'Incomplete', reviewed_at = now(),
				reviewed_by_user_id = (select user_id from users where user_id <> ? limit 1) where seller_id = ?""",
				rejected, rejected);
		UUID banned = seller("Banned " + tag, true);
		jdbc.update("update sellers set status = 'BANNED', ban_reason = 'Fraud' where seller_id = ?", banned);
		UUID disabled = seller("Disabled " + tag, true);
		jdbc.update("update users set status = 'DISABLED', disabled_reason = 'Fraud' where user_id = ?", disabled);
		UUID roleRevoked = seller("Revoked " + tag, true);
		tx.executeWithoutResult(status -> roleAssignments.revoke(users.findById(roleRevoked).orElseThrow(),
				RoleCode.SELLER));

		call(HttpMethod.GET, "/sellers?q=" + tag, null, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].sellerId", contains(visible.toString())))
				.andExpect(jsonPath("$.meta.totalItems").value(1));
		call(HttpMethod.GET, "/sellers/" + visible, null, null).andExpect(status().isOk());
		for (UUID hidden : new UUID[] { pending, rejected, banned, disabled, roleRevoked }) {
			call(HttpMethod.GET, "/sellers/" + hidden, null, null).andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
		}
	}

	@Test
	void searchIgnoresCaseAndListsByName() throws Exception {
		String tag = tag();
		UUID beta = seller("Beta Shop " + tag, true);
		UUID alpha = seller("alpha shop " + tag, true);

		call(HttpMethod.GET, "/sellers?q=" + tag.toUpperCase(), null, null)
				.andExpect(jsonPath("$.data[*].sellerId", contains(alpha.toString(), beta.toString())));
		call(HttpMethod.GET, "/sellers?q=BETA SHOP " + tag, null, null)
				.andExpect(jsonPath("$.data[*].sellerId", contains(beta.toString())));
	}

	@Test
	void likeWildcardsInTheQueryAreLiteral() throws Exception {
		String tag = tag();
		seller("Shop " + tag, true);

		// MockMvc encodes the URL, so this sends a literal % (as %25).
		call(HttpMethod.GET, "/sellers?q=%" + tag, null, null).andExpect(jsonPath("$.data", empty()));
		call(HttpMethod.GET, "/sellers?q=Sho_ " + tag, null, null).andExpect(jsonPath("$.data", empty()));
	}

	@Test
	void listIsPaged() throws Exception {
		String tag = tag();
		for (int i = 1; i <= 3; i++) {
			seller("Paged " + i + " " + tag, true);
		}

		call(HttpMethod.GET, "/sellers?q=" + tag + "&page=2&size=2", null, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].sellerName", contains("Paged 3 " + tag)))
				.andExpect(jsonPath("$.meta.page").value(2))
				.andExpect(jsonPath("$.meta.size").value(2))
				.andExpect(jsonPath("$.meta.totalItems").value(3))
				.andExpect(jsonPath("$.meta.totalPages").value(2));
	}

	@Test
	void sellerPublicHasOnlyPublicFields() throws Exception {
		UUID id = seller("Public " + tag(), true);
		jdbc.update("update sellers set description = 'Handmade', logo_object_key = ? where seller_id = ?",
				"seller-logos/" + id + "/logo.png", id);

		String body = body(call(HttpMethod.GET, "/sellers/" + id, null, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.description").value("Handmade"))
				.andExpect(jsonPath("$.data.logoUrl", containsString("seller-logos/" + id + "/logo.png"))));
		assertThat(JsonPath.<Map<String, Object>>read(body, "$.data"))
				.containsOnlyKeys("sellerId", "sellerName", "description", "logoUrl");
	}

	@Test
	void unknownSellerIsNotFoundAndAMalformedIdIsAValidationError() throws Exception {
		call(HttpMethod.GET, "/sellers/" + UUID.randomUUID(), null, null).andExpect(status().isNotFound());
		call(HttpMethod.GET, "/sellers/not-a-uuid", null, null).andExpect(status().isBadRequest());
	}

	private static String tag() {
		return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	/** Registers a seller named {@code sellerName}; approved (with the SELLER role) or left PENDING. */
	private UUID seller(String sellerName, boolean approved) throws Exception {
		UUID id = UUID.fromString(read(body(registerSeller(uniqueEmail(), sellerName, uniquePhone())
				.andExpect(status().isCreated())), "$.data.userId"));
		if (approved) {
			approveSeller(id);
		}
		return id;
	}

}
