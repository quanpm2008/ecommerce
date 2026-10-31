package com.example.identity_service.admin;

import com.example.identity_service.EndpointTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin lists and details (API §6.1, UC-E01, E06, checklist section 7). Searches use a fresh tag in names so rows
 * from other tests never match.
 */
class AdminReadEndpointsTests extends EndpointTestSupport {

	@Test
	void customerListSearchesFiltersAndSorts() throws Exception {
		Session admin = admin();
		String tag = tag();
		UUID bao = namedCustomer("Bao " + tag);
		UUID an = namedCustomer("An " + tag);
		UUID banned = namedCustomer("Cuong " + tag);
		jdbc.update("""
				update customers set status = 'BANNED', ban_reason = 'Spam', ban_mode = 'CUSTOMER_SCOPE',
				has_paid_order_before_ban = false where customer_id = ?""", banned);

		call(HttpMethod.GET, "/admin/customers?q=" + tag.toUpperCase() + "&sort=fullName,asc", null,
				admin.accessToken()).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].user.userId", contains(an.toString(), bao.toString(),
						banned.toString())))
				.andExpect(jsonPath("$.meta.totalItems").value(3));
		call(HttpMethod.GET, "/admin/customers?q=" + tag + "&status=BANNED", null, admin.accessToken())
				.andExpect(jsonPath("$.data[*].user.userId", contains(banned.toString())))
				.andExpect(jsonPath("$.data[0].banReason").value("Spam"))
				.andExpect(jsonPath("$.data[0].hasPaidOrder").value(false))
				.andExpect(jsonPath("$.data[0].customerProfile.accessMode").value("BLOCKED"));
	}

	@Test
	void customerSearchMatchesEmailAndPhone() throws Exception {
		Session admin = admin();
		Session customer = customer();
		String phone = jdbc.queryForObject("select phone_number from users where user_id = ?", String.class,
				customer.userId());

		call(HttpMethod.GET, "/admin/customers?q=" + customer.email().toUpperCase(), null, admin.accessToken())
				.andExpect(jsonPath("$.data[*].user.userId", contains(customer.userId().toString())));
		call(HttpMethod.GET, "/admin/customers?q=" + phone, null, admin.accessToken())
				.andExpect(jsonPath("$.data[*].user.userId", contains(customer.userId().toString())));
	}

	@Test
	void unknownSortKeyIsAValidationError() throws Exception {
		call(HttpMethod.GET, "/admin/customers?sort=passwordHash", null, admin().accessToken())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("sort"));
	}

	@Test
	void customerDetailHasNoCredentials() throws Exception {
		Session admin = admin();
		Session customer = customer();

		call(HttpMethod.GET, "/admin/customers/" + customer.userId(), null, admin.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.email").value(customer.email()))
				.andExpect(jsonPath("$.data.customerProfile.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.hasPaidOrder").isEmpty())
				.andExpect(content().string(not(containsString("passwordHash"))))
				.andExpect(content().string(not(containsString("googleSubject"))))
				.andExpect(content().string(not(containsString("$2a$"))));
		call(HttpMethod.GET, "/admin/customers/" + UUID.randomUUID(), null, admin.accessToken())
				.andExpect(status().isNotFound());
	}

	@Test
	void pendingQueueListsOldestFirst() throws Exception {
		Session admin = admin();
		String tag = tag();
		UUID first = seller("First " + tag);
		UUID second = seller("Second " + tag);
		UUID approved = seller("Approved " + tag);
		approveSeller(approved);
		jdbc.update("update sellers set registered_at = registered_at + interval '1 minute' where seller_id = ?",
				second);

		call(HttpMethod.GET, "/admin/sellers?q=" + tag + "&approvalStatus=PENDING&sort=registeredAt,asc", null,
				admin.accessToken()).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].user.userId", contains(first.toString(), second.toString())))
				.andExpect(jsonPath("$.data[0].sellerProfile.approvalStatus").value("PENDING"))
				.andExpect(jsonPath("$.data[0].sellerProfile.contactPhone").isNotEmpty());
		call(HttpMethod.GET, "/admin/sellers?q=" + tag + "&approvalStatus=APPROVED", null, admin.accessToken())
				.andExpect(jsonPath("$.data[*].user.userId", contains(approved.toString())))
				.andExpect(jsonPath("$.data[0].reviewedByUserId").isNotEmpty());
	}

	@Test
	void sellerDetailAndPermissions() throws Exception {
		Session seller = pendingSeller();

		call(HttpMethod.GET, "/admin/sellers/" + seller.userId(), null, admin().accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.email").value(seller.email()))
				.andExpect(content().string(not(containsString("passwordHash"))));
		call(HttpMethod.GET, "/admin/sellers/" + seller.userId(), null, seller.accessToken())
				.andExpect(status().isForbidden());
		call(HttpMethod.GET, "/admin/customers", null, customer().accessToken()).andExpect(status().isForbidden());
		call(HttpMethod.GET, "/admin/customers", null, null).andExpect(status().isUnauthorized());
	}

	private static String tag() {
		return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	private UUID namedCustomer(String fullName) throws Exception {
		String body = body(postJson("/register", json("email", uniqueEmail(), "fullName", fullName, "phoneNumber",
				uniquePhone(), "password", PASSWORD, "confirmPassword", PASSWORD)).andExpect(status().isCreated()));
		return UUID.fromString(read(body, "$.data.userId"));
	}

	private UUID seller(String sellerName) throws Exception {
		return UUID.fromString(read(body(registerSeller(uniqueEmail(), sellerName, uniquePhone())
				.andExpect(status().isCreated())), "$.data.userId"));
	}

}
