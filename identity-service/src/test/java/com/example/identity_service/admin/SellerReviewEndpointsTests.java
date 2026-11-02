package com.example.identity_service.admin;

import com.example.identity_service.EndpointTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /admin/sellers/{id}/approve} and {@code /reject} (API §6.1, UC-E06, checklist section 7).
 */
class SellerReviewEndpointsTests extends EndpointTestSupport {

	@Test
	void approveGrantsSellerAndWritesAuditAndEventTogether() throws Exception {
		Session admin = admin();
		Session seller = pendingSeller();
		long before = authzVersion(seller.userId());

		call(HttpMethod.POST, approve(seller.userId()), null, admin.accessToken()).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.sellerProfile.approvalStatus").value("APPROVED"))
				.andExpect(jsonPath("$.data.reviewedByUserId").value(admin.userId().toString()))
				.andExpect(jsonPath("$.data.user.email").value(seller.email()));

		assertThat(authzVersion(seller.userId())).isGreaterThan(before);
		assertThat(audits("APPROVE", seller.userId())).isEqualTo(1);
		assertThat(audits("GRANT_ROLE", seller.userId())).isEqualTo(1);
		assertThat(events("seller.approved", seller.userId())).isEqualTo(1);
		assertThat(events("user.roles_changed", seller.userId())).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
				select payload -> 'payload' ->> 'authzVersion' from outbox_events
				where event_type = 'seller.approved' and aggregate_id = ?""", String.class, seller.userId()))
				.isEqualTo(String.valueOf(authzVersion(seller.userId())));
		call(HttpMethod.GET, "/users/me", null, seller.accessToken()).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("TOKEN_STALE"));
		String fresh = read(loginBody(seller.email()), "$.data.accessToken");
		call(HttpMethod.GET, "/users/me", null, fresh).andExpect(jsonPath("$.data.roles", contains("SELLER")));
	}

	@Test
	void secondDecisionIsAlreadyProcessed() throws Exception {
		Session admin = admin();
		Session seller = pendingSeller();
		call(HttpMethod.POST, approve(seller.userId()), null, admin.accessToken()).andExpect(status().isOk());

		call(HttpMethod.POST, approve(seller.userId()), null, admin().accessToken())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ALREADY_PROCESSED"))
				.andExpect(jsonPath("$.error.details[0].currentStatus").value("APPROVED"));
		call(HttpMethod.POST, reject(seller.userId()), json("reason", "Too late"), admin.accessToken())
				.andExpect(status().isConflict());
		assertThat(audits("APPROVE", seller.userId())).isEqualTo(1);
		assertThat(events("seller.approved", seller.userId())).isEqualTo(1);
	}

	@Test
	void adminCannotReviewTheirOwnApplication() throws Exception {
		Session admin = admin();
		call(HttpMethod.POST, "/users/me/seller-application",
				json("sellerName", "Admin Shop " + UUID.randomUUID(), "contactPhone", uniquePhone()),
				admin.accessToken()).andExpect(status().isCreated());

		call(HttpMethod.POST, approve(admin.userId()), null, admin.accessToken()).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("SELF_APPROVAL_FORBIDDEN"));
		call(HttpMethod.POST, reject(admin.userId()), json("reason", "No"), admin.accessToken())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("SELF_APPROVAL_FORBIDDEN"));
	}

	@Test
	void rejectKeepsTheAccountAndFreesNameAndPhone() throws Exception {
		Session admin = admin();
		String name = "Rejected Shop " + UUID.randomUUID();
		String phone = uniquePhone();
		String email = uniqueEmail();
		UUID sellerId = UUID.fromString(read(body(registerSeller(email, name, phone).andExpect(status().isCreated())),
				"$.data.userId"));
		long before = authzVersion(sellerId);

		call(HttpMethod.POST, reject(sellerId), json("reason", "  Missing documents "), admin.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.sellerProfile.approvalStatus").value("REJECTED"))
				.andExpect(jsonPath("$.data.sellerProfile.rejectionReason").value("Missing documents"))
				.andExpect(jsonPath("$.data.user.userStatus").value("ACTIVE"));

		assertThat(authzVersion(sellerId)).isGreaterThan(before);
		assertThat(audits("REJECT", sellerId)).isEqualTo(1);
		assertThat(events("seller.rejected", sellerId)).isEqualTo(1);
		call(HttpMethod.GET, "/users/me", null, read(loginBody(email), "$.data.accessToken"))
				.andExpect(jsonPath("$.data.roles", empty()));
		registerSeller(uniqueEmail(), name, phone).andExpect(status().isCreated());
	}

	@Test
	void rejectNeedsAReason() throws Exception {
		Session admin = admin();
		Session seller = pendingSeller();

		call(HttpMethod.POST, reject(seller.userId()), "{}", admin.accessToken()).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("reason"));
		call(HttpMethod.POST, reject(seller.userId()), json("reason", "x".repeat(1001)), admin.accessToken())
				.andExpect(status().isBadRequest());
	}

	@Test
	void reviewNeedsSellerApproveAndAnExistingSeller() throws Exception {
		Session seller = pendingSeller();

		call(HttpMethod.POST, approve(seller.userId()), null, customer().accessToken())
				.andExpect(status().isForbidden());
		call(HttpMethod.POST, approve(seller.userId()), null, null).andExpect(status().isUnauthorized());
		call(HttpMethod.POST, approve(UUID.randomUUID()), null, admin().accessToken())
				.andExpect(status().isNotFound());
	}

	private long authzVersion(UUID userId) {
		return jdbc.queryForObject("select authz_version from users where user_id = ?", Long.class, userId);
	}

	private static String approve(UUID sellerId) {
		return "/admin/sellers/" + sellerId + "/approve";
	}

	private static String reject(UUID sellerId) {
		return "/admin/sellers/" + sellerId + "/reject";
	}

}
