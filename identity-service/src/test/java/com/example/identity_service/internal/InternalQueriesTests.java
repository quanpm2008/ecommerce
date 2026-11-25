package com.example.identity_service.internal;

import com.example.identity_service.EndpointTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.example.identity_service.TestRegions.BA_DINH;
import static com.example.identity_service.TestRegions.HANOI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Identity's internal read and touch endpoints (API §7.1, checklist section 8), called as order-service or
 * notification-service.
 */
class InternalQueriesTests extends EndpointTestSupport {

	@Test
	void saleEvidencePreservesTheDecisionFromBeforeABan() throws Exception {
		Session seller = approvedSeller();
		Instant captured = jdbc.queryForObject("select clock_timestamp()", java.sql.Timestamp.class).toInstant();
		jdbc.update("update sellers set status = 'BANNED', ban_reason = 'Review' where seller_id = ?", seller.userId());
		Instant afterBan = jdbc.queryForObject("select clock_timestamp()", java.sql.Timestamp.class).toInstant();
		String body = """
				{"completedAt":"%s","sellerIds":["%s"]}
				""";
		internal(HttpMethod.POST, "/sellers/sale-evidence", body.formatted(captured, seller.userId()), ORDER_TOKEN, null)
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].decision").value("ELIGIBLE"));
		internal(HttpMethod.POST, "/sellers/sale-evidence", body.formatted(afterBan, seller.userId()), ORDER_TOKEN, null)
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].decision").value("INELIGIBLE"));
		internal(HttpMethod.POST, "/sellers/sale-evidence", body.formatted(Instant.EPOCH, seller.userId()), ORDER_TOKEN, null)
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].decision").value("UNKNOWN"));
	}

	@Test
	void sellerStatusesFollowG06WithoutPii() throws Exception {
		Session approved = approvedSeller();
		Session pending = pendingSeller();
		Session banned = approvedSeller();
		jdbc.update("update sellers set status = 'BANNED', ban_reason = 'Fraud' where seller_id = ?", banned.userId());
		UUID unknown = UUID.randomUUID();

		internal(HttpMethod.GET, "/sellers/status?ids=" + pending.userId() + "," + unknown + "," + approved.userId()
				+ "," + banned.userId(), null, ORDER_TOKEN, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].sellerId", contains(pending.userId().toString(),
						approved.userId().toString(), banned.userId().toString())))
				.andExpect(jsonPath("$.data[*].sellable", contains(false, true, false)))
				.andExpect(jsonPath("$.data[*].sellerRoleActive", contains(false, true, true)))
				.andExpect(jsonPath("$.data[2].status").value("BANNED"))
				.andExpect(content().string(not(containsString("@example.test"))));
	}

	@Test
	void sellerStatusesTakeOneToAHundredIds() throws Exception {
		String tooMany = Stream.generate(UUID::randomUUID).limit(101).map(UUID::toString)
				.collect(Collectors.joining(","));
		internal(HttpMethod.GET, "/sellers/status?ids=" + tooMany, null, ORDER_TOKEN, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("ids"));
		internal(HttpMethod.GET, "/sellers/status?ids=", null, ORDER_TOKEN, null).andExpect(status().isBadRequest());
	}

	@Test
	void checkoutEligibilityNeedsPhoneAddressAndNoBan() throws Exception {
		Session customer = customer();
		String path = "/customers/" + customer.userId() + "/checkout-eligibility";

		internal(HttpMethod.GET, path, null, ORDER_TOKEN, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasPhone").value(true))
				.andExpect(jsonPath("$.data.addressCount").value(0))
				.andExpect(jsonPath("$.data.canCheckout").value(false));

		createAddress(customer);
		internal(HttpMethod.GET, path, null, ORDER_TOKEN, null)
				.andExpect(jsonPath("$.data.addressCount").value(1))
				.andExpect(jsonPath("$.data.canCheckout").value(true))
				.andExpect(jsonPath("$.data.authzVersion").value(2));

		jdbc.update("""
				update customers set status = 'BANNED', ban_reason = 'Chargebacks' where customer_id = ?""", customer.userId());
		internal(HttpMethod.GET, path, null, ORDER_TOKEN, null)
				.andExpect(jsonPath("$.data.customerStatus").value("BANNED"))
				.andExpect(jsonPath("$.data.paymentBlocked").value(true))
				.andExpect(jsonPath("$.data.canCheckout").value(false));

		internal(HttpMethod.GET, "/customers/" + pendingSeller().userId() + "/checkout-eligibility", null,
				ORDER_TOKEN, null).andExpect(status().isNotFound());
	}

	@Test
	void addressSnapshotIsScopedToTheCustomerAndComplete() throws Throwable {
		Session customer = customer();
		String addressId = createAddress(customer);

		internal(HttpMethod.GET, "/customers/" + customer.userId() + "/addresses/" + addressId, null, ORDER_TOKEN,
				null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.customerId").value(customer.userId().toString()))
				.andExpect(jsonPath("$.data.provinceName").value("Thành phố Hà Nội"))
				.andExpect(jsonPath("$.data.wardName").value("Phường Ba Đình"))
				.andExpect(jsonPath("$.data.addressDetail").value("12 Tran Phu"));
		internal(HttpMethod.GET, "/customers/" + customer().userId() + "/addresses/" + addressId, null, ORDER_TOKEN,
				null).andExpect(status().isNotFound());

		withoutRegionSeed(() -> internal(HttpMethod.GET, "/customers/" + customer.userId() + "/addresses/" + addressId,
				null, ORDER_TOKEN, null).andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error.code").value("REGION_SOURCE_UNAVAILABLE")));
	}

	@Test
	void touchMarksTheAddressUsedOncePerCheckoutSession() throws Exception {
		Session customer = customer();
		String addressId = createAddress(customer);
		String touch = "/customers/" + customer.userId() + "/addresses/" + addressId + "/touch";

		String first = read(body(internal(HttpMethod.POST, touch, json("checkoutSessionId", "cs-1"), ORDER_TOKEN, null)
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(true))), "$.data.lastUsedAt");
		clock.advance(Duration.ofMinutes(5));
		internal(HttpMethod.POST, touch, json("checkoutSessionId", "cs-1"), ORDER_TOKEN, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.applied").value(false))
				.andExpect(jsonPath("$.data.lastUsedAt").value(first));
		internal(HttpMethod.POST, touch, json("checkoutSessionId", "cs-2"), ORDER_TOKEN, null)
				.andExpect(jsonPath("$.data.applied").value(true))
				.andExpect(jsonPath("$.data.lastUsedAt").value(not(first)));

		// Deleted after the checkout: the touch is skipped without an error (API §7.1); the order has its snapshot.
		call(HttpMethod.DELETE, "/customers/me/addresses/" + addressId, null, customer.accessToken())
				.andExpect(status().isNoContent());
		internal(HttpMethod.POST, touch, json("checkoutSessionId", "cs-3"), ORDER_TOKEN, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.applied").value(false))
				.andExpect(jsonPath("$.data.lastUsedAt").isEmpty());
		assertThat(jdbc.queryForObject("select count(*) from customer_addresses where address_id = ?", Integer.class,
				UUID.fromString(addressId))).isZero();
	}

	@Test
	void touchNeedsACheckoutSessionAndTheOwner() throws Exception {
		Session customer = customer();
		String addressId = createAddress(customer);

		internal(HttpMethod.POST, "/customers/" + customer.userId() + "/addresses/" + addressId + "/touch",
				json("checkoutSessionId", " "), ORDER_TOKEN, null).andExpect(status().isBadRequest());
		// Another customer's address: nothing happens to it.
		internal(HttpMethod.POST, "/customers/" + customer().userId() + "/addresses/" + addressId + "/touch",
				json("checkoutSessionId", "cs-1"), ORDER_TOKEN, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.applied").value(false));
		assertThat(jdbc.queryForObject("select last_used_at from customer_addresses where address_id = ?",
				java.sql.Timestamp.class, UUID.fromString(addressId))).isNull();
	}

	@Test
	void contactGivesOnlyEmailAndName() throws Exception {
		Session customer = customer();

		internal(HttpMethod.GET, "/users/" + customer.userId() + "/contact", null, NOTIFICATION_TOKEN, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value(customer.email()))
				.andExpect(jsonPath("$.data.fullName").value("Nguyen Van An"))
				.andExpect(jsonPath("$.data.phoneNumber").doesNotExist());
		internal(HttpMethod.GET, "/users/" + UUID.randomUUID() + "/contact", null, NOTIFICATION_TOKEN, null)
				.andExpect(status().isNotFound());
	}

	private String createAddress(Session customer) throws Exception {
		return read(body(call(HttpMethod.POST, "/customers/me/addresses", json("recipientName", "Le Van D",
				"recipientPhone", uniquePhone(), "provinceCode", HANOI, "wardCode", BA_DINH, "addressDetail",
				"12 Tran Phu"), customer.accessToken()).andExpect(status().isCreated())), "$.data.addressId");
	}

}
