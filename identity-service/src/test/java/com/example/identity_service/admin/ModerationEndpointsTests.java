package com.example.identity_service.admin;

import com.example.identity_service.EndpointTestSupport;
import com.example.identity_service.obligation.AccountObligations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The ban lifecycle (G-14, UC-E03, API §3.2, §6.1 ban/unban, §7.3 J7; AC-IDN-003, 008, 010, 011). Obligations come
 * from {@code FakeObligations}; J7 is run by calling {@link ModerationService#reconcile}.
 */
class ModerationEndpointsTests extends EndpointTestSupport {

	private static final String FRAUD = "Gian lận/lừa đảo";

	@Autowired
	ModerationService moderation;

	// --- ban ---

	@Test
	void openObligationsMeanBannedWithALimitedSession() throws Exception {
		Session admin = admin();
		Session customer = customer();
		obligations.openOrders(customer.userId());

		ban(admin, customer.userId(), FRAUD).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accountType").value("CUSTOMER"))
				.andExpect(jsonPath("$.data.accountStatus").value("BANNED"))
				.andExpect(jsonPath("$.data.userStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.data.customerStatus").value("BANNED"))
				.andExpect(jsonPath("$.data.accessMode").value("OPEN_ORDERS_ONLY"))
				.andExpect(jsonPath("$.data.bannedAt").value(clock.instant().toString()))
				.andExpect(jsonPath("$.data.disabledAt").isEmpty())
				.andExpect(jsonPath("$.data.restoreUntil").isEmpty())
				.andExpect(jsonPath("$.data.obligations.hasOpenOrders").value(true))
				.andExpect(jsonPath("$.data.obligations.availableBalanceVnd").value(0));

		assertThat(events("customer.banned", customer.userId())).isEqualTo(1);
		assertThat(events("user.disabled", customer.userId())).isZero();
		assertThat(audits("BAN", customer.userId())).isEqualTo(1);
		call(HttpMethod.GET, "/users/me", null, customer.accessToken()).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("TOKEN_STALE"));
		String token = read(loginBody(customer.email()), "$.data.accessToken");
		call(HttpMethod.GET, "/users/me", null, token).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accountStatus").value("BANNED"))
				.andExpect(jsonPath("$.data.accessMode").value("OPEN_ORDERS_ONLY"))
				.andExpect(jsonPath("$.data.permissions", hasItem("order:read:own")))
				.andExpect(jsonPath("$.data.permissions", not(hasItem("cart:manage:own"))))
				.andExpect(jsonPath("$.data.permissions", not(hasItem("account:update:own"))));
		call(HttpMethod.GET, "/customers/me/addresses", null, token).andExpect(status().isForbidden());
	}

	@Test
	void nothingLeftMeansDisabledWithA30DayRestoreWindow() throws Exception {
		Session admin = admin();
		Session customer = customer();
		Instant now = clock.instant();

		ban(admin, customer.userId(), "Khác: Spam").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accountStatus").value("DISABLED"))
				.andExpect(jsonPath("$.data.userStatus").value("DISABLED"))
				.andExpect(jsonPath("$.data.accessMode").value("LOGIN_BLOCKED"))
				.andExpect(jsonPath("$.data.disabledAt").value(now.toString()))
				.andExpect(jsonPath("$.data.restoreUntil").value(now.plus(Duration.ofDays(30)).toString()))
				.andExpect(jsonPath("$.data.obligations.hasOpenOrders").value(false));

		assertThat(events("customer.banned", customer.userId())).isEqualTo(1);
		assertThat(events("user.disabled", customer.userId())).isEqualTo(1);
		call(HttpMethod.GET, "/users/me", null, customer.accessToken()).andExpect(status().isUnauthorized());
		login(customer.email(), PASSWORD).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ACCOUNT_DISABLED"));
	}

	@ParameterizedTest
	@CsvSource({ "true, 0, 5000, false, ORDERS_AND_WITHDRAWAL", "true, 0, 0, false, OPEN_ORDERS_ONLY",
			"false, 0, 5000, false, WITHDRAWAL_ONLY", "false, 7000, 0, false, OBLIGATIONS_READ_ONLY",
			"false, 0, 0, true, OBLIGATIONS_READ_ONLY" })
	void aSellersAccessModeFollowsItsOrdersAndMoney(boolean orders, long pending, long available,
			boolean pendingFinance, String mode) throws Exception {
		Session admin = admin();
		Session seller = approvedSeller();
		obligations.set(seller.userId(),
				new AccountObligations(orders, false, false, pendingFinance, pending, available, null));

		ban(admin, seller.userId(), "Kinh doanh hàng cấm").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accountStatus").value("BANNED"))
				.andExpect(jsonPath("$.data.sellerStatus").value("BANNED"))
				.andExpect(jsonPath("$.data.accessMode").value(mode));
		assertThat(events("seller.banned", seller.userId())).isEqualTo(1);
	}

	@Test
	void obligationsThatCannotBeReadAre503AndNothingIsWritten() throws Exception {
		Session admin = admin();
		Session customer = customer();
		obligations.setAvailable(false);

		ban(admin, customer.userId(), FRAUD).andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));

		assertThat(jdbc.queryForMap("select status, banned_at from users where user_id = ?", customer.userId()))
				.containsEntry("status", "ACTIVE").containsEntry("banned_at", null);
		assertThat(jdbc.queryForObject("select status from customers where customer_id = ?", String.class,
				customer.userId())).isEqualTo("ACTIVE");
		assertThat(events("customer.banned", customer.userId())).isZero();
		assertThat(audits("BAN", customer.userId())).isZero();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "{\"reason\":\"Gian lận/lừa đảo\",\"scope\":\"CUSTOMER\"}|scope",
			"{\"reason\":\"Kinh doanh hàng cấm\"}|reason", "{\"reason\":\"Khác\"}|reason", "{}|reason" })
	void aBanTakesOnlyAP21dReasonFitForTheAccount(String body, String field) throws Exception {
		Session admin = admin();
		Session customer = customer();

		call(HttpMethod.POST, "/admin/ban/" + customer.userId(), body, admin.accessToken())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.details[0].field").value(field));
		assertThat(obligations.calls()).isZero();
	}

	@Test
	void onlyApprovedSellersAndCustomersCanBeBanned() throws Exception {
		Session admin = admin();

		ban(admin, admin().userId(), FRAUD).andExpect(status().isForbidden());
		ban(admin, pendingSeller().userId(), FRAUD).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"))
				.andExpect(jsonPath("$.error.details[0].currentStatus").value("PENDING"));
		ban(admin, UUID.randomUUID(), FRAUD).andExpect(status().isNotFound());
		call(HttpMethod.POST, "/admin/ban/" + customer().userId(), json("reason", FRAUD), customer().accessToken())
				.andExpect(status().isForbidden());
	}

	@Test
	void banningAgainChangesAndAnnouncesNothing() throws Exception {
		Session admin = admin();
		Session customer = customer();
		obligations.openOrders(customer.userId());
		ban(admin, customer.userId(), FRAUD).andExpect(status().isOk());
		Map<String, Object> before = state(customer.userId());
		clock.advance(Duration.ofHours(1));

		ban(admin, customer.userId(), "Khác: again").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accountStatus").value("BANNED"));

		assertThat(state(customer.userId())).isEqualTo(before);
		assertThat(events("customer.banned", customer.userId())).isEqualTo(1);
		assertThat(audits("BAN", customer.userId())).isEqualTo(1);
	}

	// --- J7 and sign-in reconciliation ---

	@Test
	void j7DisablesOnceWhenEverythingIsGoneAndReopensOnALateObligation() throws Exception {
		Session admin = admin();
		Session customer = customer();
		obligations.openOrders(customer.userId());
		ban(admin, customer.userId(), FRAUD).andExpect(status().isOk());
		Instant bannedAt = clock.instant();
		clock.advance(Duration.ofDays(2));
		obligations.clear(customer.userId());

		assertThat(moderation.reconcile(customer.userId())).isTrue();
		assertThat(moderation.reconcile(customer.userId())).isTrue();

		Map<String, Object> disabled = state(customer.userId());
		assertThat(disabled).containsEntry("status", "DISABLED").containsEntry("access_mode", "LOGIN_BLOCKED");
		assertThat(events("user.disabled", customer.userId())).isEqualTo(1);
		assertThat(audits("DISABLE", customer.userId())).isEqualTo(1);

		clock.advance(Duration.ofDays(3));
		obligations.openOrders(customer.userId());
		moderation.reconcile(customer.userId());

		assertThat(state(customer.userId())).containsEntry("status", "ACTIVE")
				.containsEntry("access_mode", "OPEN_ORDERS_ONLY")
				.containsEntry("banned_at", disabled.get("banned_at"))
				.containsEntry("disabled_at", disabled.get("disabled_at"))
				.containsEntry("restore_until", disabled.get("restore_until"));
		assertThat(jdbc.queryForObject("select status from customers where customer_id = ?", String.class,
				customer.userId())).isEqualTo("BANNED");
		assertThat(events("user.enabled", customer.userId())).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
				select payload -> 'payload' ->> 'accountStatus' from outbox_events
				where event_type = 'user.enabled' and aggregate_id = ?""", String.class, customer.userId()))
				.isEqualTo("BANNED");
		assertThat(events("customer.unbanned", customer.userId())).isZero();

		obligations.clear(customer.userId());
		moderation.reconcile(customer.userId());
		assertThat(state(customer.userId())).containsEntry("status", "DISABLED")
				.containsEntry("disabled_at", disabled.get("disabled_at"));
		assertThat(java.sql.Timestamp.class.cast(disabled.get("banned_at")).toInstant()).isEqualTo(bannedAt);
	}

	@Test
	void j7KeepsTheAccountWhenObligationsCannotBeRead() throws Exception {
		Session admin = admin();
		Session customer = customer();
		obligations.openOrders(customer.userId());
		ban(admin, customer.userId(), FRAUD).andExpect(status().isOk());
		Map<String, Object> before = state(customer.userId());
		obligations.clear(customer.userId());
		obligations.setAvailable(false);

		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> moderation.reconcile(customer.userId())))
				.isInstanceOf(com.example.identity_service.common.error.ApiException.class);

		assertThat(state(customer.userId())).isEqualTo(before);
	}

	@Test
	void signingInReconcilesTheBan() throws Exception {
		Session admin = admin();
		Session disabled = customer();
		ban(admin, disabled.userId(), FRAUD).andExpect(status().isOk());
		Session banned = customer();
		obligations.openOrders(banned.userId());
		ban(admin, banned.userId(), FRAUD).andExpect(status().isOk());

		obligations.openOrders(disabled.userId());
		login(disabled.email(), PASSWORD).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessMode").value("OPEN_ORDERS_ONLY"))
				.andExpect(jsonPath("$.data.profile.accountStatus").value("BANNED"));

		obligations.clear(banned.userId());
		login(banned.email(), PASSWORD).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ACCOUNT_DISABLED"));

		obligations.setAvailable(false);
		login(disabled.email(), PASSWORD).andExpect(status().isServiceUnavailable());
	}

	// --- unban ---

	@Test
	void aBannedAccountIsLiftedAtAnyTime() throws Exception {
		Session admin = admin();
		Session seller = approvedSeller();
		obligations.openOrders(seller.userId());
		ban(admin, seller.userId(), "Kinh doanh hàng giả/vi phạm sở hữu trí tuệ").andExpect(status().isOk());
		clock.advance(Duration.ofDays(90));

		unban(admin, seller.userId(), json("reason", "Appeal accepted")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.data.sellerStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.data.accessMode").value("FULL"))
				.andExpect(jsonPath("$.data.bannedAt").isEmpty())
				.andExpect(jsonPath("$.data.obligations").isEmpty());

		assertThat(events("seller.unbanned", seller.userId())).isEqualTo(1);
		assertThat(events("user.enabled", seller.userId())).isZero();
		assertThat(audits("UNBAN", seller.userId())).isEqualTo(1);
		String token = read(loginBody(seller.email()), "$.data.accessToken");
		call(HttpMethod.GET, "/users/me", null, token)
				.andExpect(jsonPath("$.data.permissions", hasItem("product:manage:own")));
	}

	@Test
	void aDisabledAccountIsLiftedUntilRestoreUntilAndNotAfter() throws Exception {
		Session admin = admin();
		Session onTime = customer();
		Session late = customer();
		ban(admin, onTime.userId(), FRAUD).andExpect(status().isOk());
		ban(admin, late.userId(), FRAUD).andExpect(status().isOk());
		clock.advance(Duration.ofDays(30));

		unban(admin, onTime.userId(), "{}").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"));
		assertThat(events("customer.unbanned", onTime.userId())).isEqualTo(1);
		assertThat(events("user.enabled", onTime.userId())).isEqualTo(1);
		login(onTime.email(), PASSWORD).andExpect(status().isOk());

		clock.advance(Duration.ofSeconds(1));
		unban(admin, late.userId(), "{}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ACCOUNT_RESTORE_WINDOW_EXPIRED"));
		assertThat(state(late.userId())).containsEntry("status", "DISABLED");
	}

	@Test
	void unbanningAnActiveAccountChangesNothingAndScopeIsRefused() throws Exception {
		Session admin = admin();
		Session customer = customer();

		unban(admin, customer.userId(), null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"));
		unban(admin, customer.userId(), json("scope", "CUSTOMER")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("scope"));
		assertThat(events("customer.unbanned", customer.userId())).isZero();
		assertThat(audits("UNBAN", customer.userId())).isZero();
		unban(admin, pendingSeller().userId(), null).andExpect(status().isConflict());
	}

	// --- admin detail ---

	@Test
	void theAdminDetailShowsWhatABanWouldDoNow() throws Exception {
		Session admin = admin();
		Session customer = customer();
		obligations.openOrders(customer.userId());

		call(HttpMethod.GET, "/admin/customers/" + customer.userId(), null, admin.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.accountStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.data.banImpact.resultingAccountStatus").value("BANNED"))
				.andExpect(jsonPath("$.data.banImpact.obligations.hasOpenOrders").value(true));
		obligations.setAvailable(false);
		call(HttpMethod.GET, "/admin/customers/" + customer.userId(), null, admin.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.banImpact").isEmpty());
	}

	private ResultActions ban(Session admin, UUID userId, String reason) throws Exception {
		return call(HttpMethod.POST, "/admin/ban/" + userId, json("reason", reason), fresh(admin));
	}

	private ResultActions unban(Session admin, UUID userId, String body) throws Exception {
		return call(HttpMethod.POST, "/admin/unban/" + userId, body, fresh(admin));
	}

	/** A token valid at the test clock's current time (tests move it by days). */
	private String fresh(Session admin) throws Exception {
		return read(loginBody(admin.email()), "$.data.accessToken");
	}

	private Map<String, Object> state(UUID userId) {
		return jdbc.queryForMap("""
				select status, access_mode, banned_at, disabled_at, restore_until, authz_version from users
				where user_id = ?""", userId);
	}

}
