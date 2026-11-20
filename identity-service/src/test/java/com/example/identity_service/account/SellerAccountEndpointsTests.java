package com.example.identity_service.account;

import com.example.identity_service.EndpointTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code PATCH /sellers/me} (API §6.1 #12, UC-A06): an APPROVED, ACTIVE seller edits only the shop description. The
 * shop name, phone and logo are the account's ({@code PATCH /users/me}, {@code AvatarEndpointsTests}).
 */
class SellerAccountEndpointsTests extends EndpointTestSupport {

	@Test
	void approvedSellerUpdatesTheDescription() throws Exception {
		Session seller = approvedSeller();

		call(HttpMethod.PATCH, "/sellers/me", json("description", "Handmade clothes"), seller.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.description").value("Handmade clothes"))
				.andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"))
				.andExpect(jsonPath("$.data.latestApplicationId").isNotEmpty());
		call(HttpMethod.PATCH, "/sellers/me", "{\"description\":null}", seller.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.description").isEmpty());
	}

	@Test
	void nothingButTheDescriptionChangesHere() throws Exception {
		Session seller = approvedSeller();

		call(HttpMethod.PATCH, "/sellers/me", json("sellerName", "Renamed", "fullName", "Renamed", "contactPhone",
				uniquePhone(), "logoObjectKey", "avatars/x.png", "email", "x@example.test"), seller.accessToken())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[*].field", containsInAnyOrder("sellerName", "fullName",
						"contactPhone", "logoObjectKey", "email")));
	}

	@Test
	void onlyAnApprovedActiveSellerMayEditTheShop() throws Exception {
		Session banned = approvedSeller();
		jdbc.update("update sellers set status = 'BANNED', ban_reason = 'Khác: Fake goods' where seller_id = ?",
				banned.userId());

		// A PENDING seller cannot even sign in (AuthEndpointsTests), so only the banned one is left to check here.
		call(HttpMethod.PATCH, "/sellers/me", json("description", "x"), banned.accessToken())
				.andExpect(status().isForbidden());
		call(HttpMethod.PATCH, "/sellers/me", json("description", "x"), customer().accessToken())
				.andExpect(status().isForbidden());
	}

}
