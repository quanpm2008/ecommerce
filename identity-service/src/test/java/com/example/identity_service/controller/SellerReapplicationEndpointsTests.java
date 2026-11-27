package com.example.identity_service.controller;

import com.example.identity_service.EndpointTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A REJECTED seller applies again without signing in (API §6.1 #30/#31, UC-A02.ALT-04): a single-use emailed token,
 * then a new SellerApplication on the same seller id; the rejected application keeps its row and reason.
 */
class SellerReapplicationEndpointsTests extends EndpointTestSupport {

	@Test
	void theChallengeAlwaysAnswersTheSameAndOnlyARejectedSellerGetsALink() throws Exception {
		Session rejected = rejectedSeller();
		Session pending = pendingSeller();
		Session approved = approvedSeller();
		String customerEmail = uniqueEmail();
		registerCustomer(customerEmail);

		List<String> bodies = new java.util.ArrayList<>();
		for (String email : List.of(rejected.email(), pending.email(), approved.email(), customerEmail, uniqueEmail())) {
			clientIp = randomIp();
			bodies.add(body(challenge(email).andExpect(status().isAccepted())));
		}

		assertThat(bodies).containsOnly(bodies.getFirst());
		assertThat(events("seller.reapplication_requested", rejected.userId())).isEqualTo(1);
		assertThat(events("seller.reapplication_requested", pending.userId())).isZero();
		assertThat(events("seller.reapplication_requested", approved.userId())).isZero();
		assertThat(jdbc.queryForObject("""
				select payload -> 'payload' ->> 'expiresAt' from outbox_events
				where event_type = 'seller.reapplication_requested' and aggregate_id = ?""", String.class,
				rejected.userId())).isEqualTo(clock.instant().plus(Duration.ofMinutes(30)).toString());
		assertThat(jdbc.queryForObject("select count(*) from seller_reapplication_tokens where seller_id = ?",
				Integer.class, rejected.userId())).isEqualTo(1);
	}

	@Test
	void aRejectedSellerAppliesAgainWithTheEmailedToken() throws Exception {
		Session seller = rejectedSeller();
		String token = tokenFor(seller);
		String newName = "Second Try " + UUID.randomUUID();
		String newPhone = uniquePhone();

		String body = body(reapply(token, newName, newPhone, "Now with real photos")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.sellerId").value(seller.userId().toString()))
				.andExpect(jsonPath("$.data.approvalStatus").value("PENDING"))
				.andExpect(jsonPath("$.data.accessToken").doesNotExist()));

		assertThat(jdbc.queryForList("""
				select submission_no || ' ' || status || ' ' || snapshot_full_name || ' ' || coalesce(rejection_reason, '-')
				from seller_applications where seller_id = ? order by submission_no""", String.class, seller.userId()))
				.containsExactly("1 REJECTED Shop of " + seller.email() + " Khác: Blurry photos",
						"2 PENDING " + newName + " -");
		assertThat(jdbc.queryForObject("""
				select application_id::text from seller_applications where seller_id = ? and submission_no = 2""",
				String.class, seller.userId())).isEqualTo(read(body, "$.data.applicationId"));
		assertThat(jdbc.queryForMap("select full_name, phone_number from users where user_id = ?", seller.userId()))
				.containsEntry("full_name", newName).containsEntry("phone_number", newPhone);
		assertThat(jdbc.queryForMap("select approval_status, description from sellers where seller_id = ?",
				seller.userId())).containsEntry("approval_status", "PENDING")
				.containsEntry("description", "Now with real photos");
		assertThat(events("seller.registered", seller.userId())).isEqualTo(2);
		login(seller.email(), PASSWORD, "SELLER").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("SELLER_PENDING_APPROVAL"));
	}

	@Test
	void theTokenWorksOnceAndOnlyForThirtyMinutes() throws Exception {
		Session first = rejectedSeller();
		String used = tokenFor(first);
		reapply(used, "Shop " + UUID.randomUUID(), uniquePhone(), null).andExpect(status().isCreated());
		Session second = rejectedSeller();
		String expiring = tokenFor(second);
		clock.advance(Duration.ofMinutes(31));

		for (String token : List.of(used, expiring, "not-a-token")) {
			reapply(token, "Shop " + UUID.randomUUID(), uniquePhone(), null).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
					.andExpect(jsonPath("$.error.details[0].field").value("verificationToken"));
		}
		assertThat(applications(second.userId())).isEqualTo(1);
	}

	@Test
	void onlyARejectedSellerAppliesAgain() throws Exception {
		Session seller = rejectedSeller();
		String one = tokenFor(seller);
		clientIp = randomIp();
		challenge(seller.email()).andExpect(status().isAccepted());
		String two = latestToken(seller);
		reapply(one, "Shop " + UUID.randomUUID(), uniquePhone(), null).andExpect(status().isCreated());

		reapply(two, "Shop " + UUID.randomUUID(), uniquePhone(), null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"))
				.andExpect(jsonPath("$.error.details[0].currentStatus").value("PENDING"));
		assertThat(applications(seller.userId())).isEqualTo(2);
	}

	@Test
	void theNewShopNameAndPhoneMustBeFree() throws Exception {
		Session seller = rejectedSeller();
		String token = tokenFor(seller);
		String takenName = "Taken " + UUID.randomUUID();
		registerSeller(uniqueEmail(), takenName, uniquePhone()).andExpect(status().isCreated());
		String takenPhone = uniquePhone();
		register(uniqueEmail(), takenPhone, PASSWORD).andExpect(status().isCreated());

		reapply(token, " " + takenName.toUpperCase() + " ", uniquePhone(), null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("SELLER_NAME_ALREADY_USED"));
		reapply(token, "Shop " + UUID.randomUUID(), takenPhone, null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("PHONE_ALREADY_USED"));
		assertThat(applications(seller.userId())).isEqualTo(1);
		reapply(token, "Shop " + UUID.randomUUID(), uniquePhone(), null).andExpect(status().isCreated());
	}

	@Test
	void theChallengeIsRateLimitedPerEmail() throws Exception {
		Session seller = rejectedSeller();

		for (int i = 0; i < 5; i++) {
			clientIp = randomIp();
			challenge(seller.email()).andExpect(status().isAccepted());
		}
		clientIp = randomIp();
		challenge(seller.email()).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
	}

	/** A seller account whose first application was rejected; its shop name is "Shop of &lt;email&gt;". */
	private Session rejectedSeller() throws Exception {
		String email = uniqueEmail();
		UUID id = UUID.fromString(read(body(registerSeller(email, "Shop of " + email, uniquePhone())
				.andExpect(status().isCreated())), "$.data.userId"));
		rejectSeller(id);
		return new Session(id, email, null);
	}

	private String tokenFor(Session seller) throws Exception {
		challenge(seller.email()).andExpect(status().isAccepted());
		return latestToken(seller);
	}

	/** The token from the newest emailed link (the outbox holds it until Notification sends it). */
	private String latestToken(Session seller) {
		String url = jdbc.queryForObject("""
				select payload -> 'payload' ->> 'verificationUrl' from outbox_events
				where event_type = 'seller.reapplication_requested' and aggregate_id = ?
				order by version desc limit 1""", String.class, seller.userId());
		return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("token");
	}

	private org.springframework.test.web.servlet.ResultActions challenge(String email) throws Exception {
		return postJson("/seller-reapplication/challenge", json("email", email));
	}

	private org.springframework.test.web.servlet.ResultActions reapply(String token, String fullName, String phone,
			String description) throws Exception {
		return postJson("/seller-reapplication", description == null
				? json("verificationToken", token, "fullName", fullName, "phoneNumber", phone)
				: json("verificationToken", token, "fullName", fullName, "phoneNumber", phone, "description",
						description));
	}

	private int applications(UUID sellerId) {
		return jdbc.queryForObject("select count(*) from seller_applications where seller_id = ?", Integer.class,
				sellerId);
	}

}
