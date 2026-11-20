package com.example.identity_service;

import com.example.identity_service.auth.AuthController;
import com.example.identity_service.rbac.RoleAssignments;
import com.example.identity_service.rbac.RoleCode;
import com.example.identity_service.user.AccountType;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for endpoint tests: one Spring context (PostgreSQL with the seeded regions, SeaweedFS, a movable
 * clock), fresh emails and phone numbers per test, and a random client IP per test so per-IP rate limits never leak
 * between tests.
 */
@SpringBootTest(properties = { "eureka.client.enabled=false",
		"identity.internal.clients.order-service=" + EndpointTestSupport.ORDER_TOKEN,
		"identity.internal.clients.notification-service=" + EndpointTestSupport.NOTIFICATION_TOKEN,
		"identity.internal.clients.gateway-service=" + EndpointTestSupport.GATEWAY_TOKEN,
		"identity.internal.clients.payment-service=too-short" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class, SeaweedfsTestConfiguration.class,
		FakeOrderClient.Configuration.class })
public abstract class EndpointTestSupport {

	protected static final String API = "/api/v1/identity";

	protected static final String PASSWORD = "Secret123";

	/** {@code X-Service-Token} values of the services configured for tests ({@code payment-service}'s is too short). */
	protected static final String ORDER_TOKEN = "order-service-test-token-0123456789abcdef";

	protected static final String NOTIFICATION_TOKEN = "notification-service-test-token-0123456789";

	protected static final String GATEWAY_TOKEN = "gateway-service-test-token-0123456789abcdef";

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected FakeOrderClient orders;

	@Autowired
	protected RoleAssignments roleAssignments;

	@Autowired
	protected UserRepository users;

	@Autowired
	protected TransactionTemplate tx;

	@Autowired
	protected PasswordEncoder passwordEncoder;

	protected String clientIp;

	@BeforeEach
	void freshClientAndClock() {
		clock.reset();
		orders.reset();
		clientIp = randomIp();
	}

	protected static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.test";
	}

	protected static String uniquePhone() {
		return "09" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
	}

	protected static String randomIp() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
	}

	/** Any endpoint under {@code /api/v1/identity}, with an optional JSON body and access token. */
	protected ResultActions call(HttpMethod method, String path, String json, String accessToken) throws Exception {
		MockHttpServletRequestBuilder request = withIp(switch (method.name()) {
			case "GET" -> get(API + path);
			case "POST" -> post(API + path);
			case "PUT" -> put(API + path);
			case "PATCH" -> patch(API + path);
			case "DELETE" -> delete(API + path);
			default -> throw new IllegalArgumentException(method.name());
		});
		if (json != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		if (accessToken != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		}
		return mvc.perform(request);
	}

	/**
	 * Any endpoint under {@code /internal/v1/identity}, as a service ({@code serviceToken} in {@code X-Service-Token},
	 * may be null), optionally carrying a user's access token as Bearer.
	 */
	protected ResultActions internal(HttpMethod method, String path, String json, String serviceToken,
			String accessToken) throws Exception {
		MockHttpServletRequestBuilder request = withIp(switch (method.name()) {
			case "GET" -> get("/internal/v1/identity" + path);
			case "POST" -> post("/internal/v1/identity" + path);
			default -> throw new IllegalArgumentException(method.name());
		});
		if (json != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		if (serviceToken != null) {
			request.header("X-Service-Token", serviceToken);
		}
		if (accessToken != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		}
		return mvc.perform(request);
	}

	/** A POST under {@code /api/v1/identity/auth}. */
	protected ResultActions postJson(String path, String json) throws Exception {
		return mvc.perform(withIp(post(AuthController.PATH + path)).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	protected ResultActions postJson(String path, String json, String accessToken) throws Exception {
		return mvc.perform(withIp(post(AuthController.PATH + path)).contentType(MediaType.APPLICATION_JSON)
				.content(json).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	protected ResultActions changePassword(String accessToken, String current, String next, String confirm)
			throws Exception {
		return call(HttpMethod.PUT, "/users/me/password",
				json("currentPassword", current, "newPassword", next, "confirmPassword", confirm), accessToken);
	}

	protected ResultActions register(String email, String phone, String password) throws Exception {
		return postJson("/register", json("email", email, "fullName", "Nguyen Van An", "phoneNumber", phone,
				"password", password, "confirmPassword", password));
	}

	/** Registers a customer and returns its user id. */
	protected String registerCustomer(String email) throws Exception {
		String body = register(email, uniquePhone(), PASSWORD).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.userId");
	}

	/** A seller account: {@code shopName} is its {@code fullName}, {@code phone} its (and the shop's) phone. */
	protected ResultActions registerSeller(String email, String shopName, String phone) throws Exception {
		return postJson("/register-seller", json("email", email, "fullName", shopName, "phoneNumber", phone,
				"password", PASSWORD, "confirmPassword", PASSWORD));
	}

	/** Logs in to the account's own channel (its account type, or CUSTOMER for an unknown email). */
	protected ResultActions login(String email, String password) throws Exception {
		return login(email, password, accountTypeOf(email));
	}

	protected ResultActions login(String email, String password, String accountType) throws Exception {
		return postJson("/login", json("email", email, "password", password, "accountType", accountType));
	}

	/** The account type stored for {@code email}, or CUSTOMER when there is no such account. */
	protected String accountTypeOf(String email) {
		return jdbc.query("select account_type from users where email = lower(?)", rs -> rs.next() ? rs.getString(1)
				: "CUSTOMER", email.strip());
	}

	/** Logs in and returns the response body. */
	protected String loginBody(String email) throws Exception {
		return login(email, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	protected ResultActions refresh(String refreshToken) throws Exception {
		return postJson("/refresh", json("refreshToken", refreshToken));
	}

	/** Registers a customer, logs in and returns the session. */
	protected Session customer() throws Exception {
		String email = uniqueEmail();
		UUID userId = UUID.fromString(registerCustomer(email));
		return new Session(userId, email, read(loginBody(email), "$.data.accessToken"));
	}

	/**
	 * Registers a seller account (PENDING, no role). A pending seller cannot sign in (API §3.2), so the session has no
	 * access token.
	 */
	protected Session pendingSeller() throws Exception {
		String email = uniqueEmail();
		String body = registerSeller(email, "Shop " + UUID.randomUUID(), uniquePhone()).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return new Session(UUID.fromString(read(body, "$.data.userId")), email, null);
	}

	/** A seller approved as an admin would (APPROVED + SELLER role), logged in afterwards. */
	protected Session approvedSeller() throws Exception {
		Session pending = pendingSeller();
		approveSeller(pending.userId());
		return new Session(pending.userId(), pending.email(), read(loginBody(pending.email()), "$.data.accessToken"));
	}

	/**
	 * An ADMIN account (own email, no Customer or Seller profile) with the ADMIN role, logged in. Admins are only made
	 * by the bootstrap in production; this is the test shortcut.
	 */
	protected Session admin() throws Exception {
		String email = uniqueEmail();
		UUID adminId = tx.execute(status -> {
			User admin = users.save(User.local(AccountType.ADMIN, email, passwordEncoder.encode(PASSWORD),
					"Admin " + email.substring(5, 13), null));
			roleAssignments.grant(admin, RoleCode.ADMIN, null);
			return admin.getUserId();
		});
		return new Session(adminId, email, read(loginBody(email), "$.data.accessToken"));
	}

	/** Marks the seller's latest application and profile REJECTED with a P-21d reason, like an admin rejection. */
	protected void rejectSeller(UUID sellerId) {
		UUID reviewer = jdbc.queryForObject("""
				insert into users (email, account_type, auth_method, password_hash, full_name)
				values (?, 'ADMIN', 'LOCAL', 'x', 'Reviewer') returning user_id""", UUID.class, uniqueEmail());
		jdbc.update("""
				update seller_applications set status = 'REJECTED', rejection_reason = 'Khác: Blurry photos',
				reviewed_at = now(), reviewed_by_user_id = ? where seller_id = ? and status = 'PENDING'""", reviewer,
				sellerId);
		jdbc.update("""
				update sellers set approval_status = 'REJECTED', rejection_reason = 'Khác: Blurry photos',
				reviewed_at = now(), reviewed_by_user_id = ? where seller_id = ?""", reviewer, sellerId);
	}

	/**
	 * Runs {@code action} while the region seed cannot be read (its dataset table renamed away), then puts it back.
	 * Test classes run one at a time, so no other test sees it.
	 */
	protected void withoutRegionSeed(org.junit.jupiter.api.function.Executable action) throws Throwable {
		jdbc.execute("alter table region_dataset rename to region_dataset_hidden");
		try {
			action.execute();
		}
		finally {
			jdbc.execute("alter table region_dataset_hidden rename to region_dataset");
		}
	}

	/** Number of outbox rows of {@code eventType} for the aggregate. */
	protected int events(String eventType, UUID aggregateId) {
		return jdbc.queryForObject("select count(*) from outbox_events where event_type = ? and aggregate_id = ?",
				Integer.class, eventType, aggregateId);
	}

	/** Number of audit rows with {@code action} on the target. */
	protected int audits(String action, UUID targetId) {
		return jdbc.queryForObject("select count(*) from admin_audit_log where action = ? and target_id = ?",
				Integer.class, action, targetId);
	}

	/** Marks the seller's latest application and profile APPROVED and grants SELLER, like an admin approval. */
	protected void approveSeller(UUID sellerId) {
		UUID reviewer = jdbc.queryForObject("""
				insert into users (email, account_type, auth_method, password_hash, full_name)
				values (?, 'ADMIN', 'LOCAL', 'x', 'Reviewer') returning user_id""", UUID.class, uniqueEmail());
		jdbc.update("""
				update seller_applications set status = 'APPROVED', reviewed_at = now(), reviewed_by_user_id = ?
				where seller_id = ? and status = 'PENDING'""", reviewer, sellerId);
		jdbc.update("""
				update sellers set approval_status = 'APPROVED', reviewed_at = now(), reviewed_by_user_id = ?
				where seller_id = ?""", reviewer, sellerId);
		tx.executeWithoutResult(status -> roleAssignments.grant(users.findById(sellerId).orElseThrow(), RoleCode.SELLER,
				reviewer));
	}

	protected UUID insertGoogleUser(String email) {
		return jdbc.queryForObject("""
				insert into users (email, account_type, auth_method, google_subject, full_name)
				values (?, 'CUSTOMER', 'GOOGLE', ?, 'Google User') returning user_id""", UUID.class, email,
				UUID.randomUUID().toString());
	}

	protected void disableUser(String email) {
		jdbc.update("update users set status = 'DISABLED', disabled_reason = 'Fraud' where email = ?", email);
	}

	/** A flat JSON object from alternating keys and string values. */
	protected static String json(String... keysAndValues) {
		StringBuilder json = new StringBuilder("{");
		for (int i = 0; i < keysAndValues.length; i += 2) {
			json.append(i == 0 ? "" : ",").append('"').append(keysAndValues[i]).append("\":\"")
					.append(keysAndValues[i + 1].replace("\"", "\\\"")).append('"');
		}
		return json.append('}').toString();
	}

	protected static String body(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString();
	}

	protected static String read(String body, String path) {
		return JsonPath.read(body, path);
	}

	private MockHttpServletRequestBuilder withIp(MockHttpServletRequestBuilder request) {
		return request.with(r -> {
			r.setRemoteAddr(clientIp);
			return r;
		});
	}

	public record Session(UUID userId, String email, String accessToken) {
	}

}
