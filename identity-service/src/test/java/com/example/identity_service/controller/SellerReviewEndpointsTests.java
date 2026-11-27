package com.example.identity_service.controller;

import com.example.identity_service.EndpointTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
		// Only now may the seller sign in, and the session has the SELLER role.
		String token = read(loginBody(seller.email()), "$.data.accessToken");
		call(HttpMethod.GET, "/users/me", null, token).andExpect(jsonPath("$.data.roles", contains("SELLER")));
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
		call(HttpMethod.POST, reject(seller.userId()), json("reason", "Khác: Too late"), admin.accessToken())
				.andExpect(status().isConflict());
		assertThat(audits("APPROVE", seller.userId())).isEqualTo(1);
		assertThat(events("seller.approved", seller.userId())).isEqualTo(1);
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

		call(HttpMethod.POST, reject(sellerId), json("reason", "  Khác:   Missing documents "), admin.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.sellerProfile.approvalStatus").value("REJECTED"))
				.andExpect(jsonPath("$.data.sellerProfile.rejectionReason").value("Khác: Missing documents"))
				.andExpect(jsonPath("$.data.user.userStatus").value("ACTIVE"));

		assertThat(authzVersion(sellerId)).isGreaterThan(before);
		assertThat(audits("REJECT", sellerId)).isEqualTo(1);
		assertThat(events("seller.rejected", sellerId)).isEqualTo(1);
		// A REJECTED seller cannot sign in, and the name and phone are free for a new registration.
		login(email, PASSWORD).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("SELLER_REJECTED"));
		// The name is free again; the phone stays the rejected account's own (phones are unique across accounts).
		registerSeller(uniqueEmail(), name, uniquePhone()).andExpect(status().isCreated());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"Thông tin gian hàng không phù hợp|Thông tin gian hàng không phù hợp",
			"Thông tin đăng ký không chính xác/không nhất quán:  name differs |Thông tin đăng ký không chính xác/không nhất quán: name differs",
			"Khác: Blurry photos|Khác: Blurry photos" })
	void rejectTakesAP21dLabelWithAnOptionalDescription(String reason, String stored) throws Exception {
		Session admin = admin();
		Session seller = pendingSeller();

		call(HttpMethod.POST, reject(seller.userId()), json("reason", reason), admin.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.sellerProfile.rejectionReason").value(stored));
		assertThat(jdbc.queryForObject("select rejection_reason from seller_applications where seller_id = ?",
				String.class, seller.userId())).isEqualTo(stored);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "Khác|DESCRIPTION_REQUIRED", "Khác:   |DESCRIPTION_REQUIRED",
			"Missing documents|UNKNOWN_REASON", "Gian lận/lừa đảo|UNKNOWN_REASON" })
	void rejectRefusesAnythingElse(String reason, String issue) throws Exception {
		Session admin = admin();
		Session seller = pendingSeller();

		call(HttpMethod.POST, reject(seller.userId()), json("reason", reason), admin.accessToken())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("reason"))
				.andExpect(jsonPath("$.error.details[0].issue").value(issue));
		assertThat(jdbc.queryForObject("select approval_status from sellers where seller_id = ?", String.class,
				seller.userId())).isEqualTo("PENDING");
	}

	@Test
	void theApplicationHistoryKeepsEverySubmission() throws Exception {
		Session admin = admin();
		Session seller = pendingSeller();
		call(HttpMethod.POST, reject(seller.userId()), json("reason", "Khác: First try"), admin.accessToken())
				.andExpect(status().isOk());
		UUID second = UUID.randomUUID();
		jdbc.update("update users set full_name = 'Renamed Shop " + second + "' where user_id = ?", seller.userId());
		jdbc.update("""
				insert into seller_applications (application_id, seller_id, submission_no, snapshot_full_name,
				snapshot_phone) select ?, seller_id, 2, 'Renamed Shop ' || ?, u.phone_number from sellers
				join users u on u.user_id = seller_id where seller_id = ?""", second, second.toString(), seller.userId());
		jdbc.update("""
				update sellers set approval_status = 'PENDING', rejection_reason = null, reviewed_at = null,
				reviewed_by_user_id = null where seller_id = ?""", seller.userId());

		call(HttpMethod.POST, approve(seller.userId()), null, admin.accessToken()).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.sellerProfile.latestApplicationId").value(second.toString()));

		call(HttpMethod.GET, "/admin/sellers/" + seller.userId() + "/applications", null, admin.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].submissionNo", contains(2, 1)))
				.andExpect(jsonPath("$.data[0].status").value("APPROVED"))
				.andExpect(jsonPath("$.data[0].fullName").value("Renamed Shop " + second))
				.andExpect(jsonPath("$.data[1].status").value("REJECTED"))
				.andExpect(jsonPath("$.data[1].rejectionReason").value("Khác: First try"))
				.andExpect(jsonPath("$.data[1].reviewedByUserId").value(admin.userId().toString()));
		call(HttpMethod.GET, "/admin/sellers/" + seller.userId() + "/applications", null, customer().accessToken())
				.andExpect(status().isForbidden());
		call(HttpMethod.GET, "/admin/sellers/" + UUID.randomUUID() + "/applications", null, admin.accessToken())
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectNeedsAReason() throws Exception {
		Session admin = admin();
		Session seller = pendingSeller();

		call(HttpMethod.POST, reject(seller.userId()), "{}", admin.accessToken()).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("reason"));
		call(HttpMethod.POST, reject(seller.userId()), json("reason", "Khác: " + "x".repeat(1001)), admin.accessToken())
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
