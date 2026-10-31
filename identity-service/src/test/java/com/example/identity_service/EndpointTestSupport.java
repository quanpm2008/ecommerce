package com.example.identity_service;

import com.example.identity_service.auth.AuthController;
import com.example.identity_service.rbac.RoleAssignments;
import com.example.identity_service.rbac.RoleCode;
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
 * Shared setup for endpoint tests: one Spring context (PostgreSQL, SeaweedFS, a fake region source, a movable
 * clock), fresh emails and phone numbers per test, and a random client IP per test so per-IP rate limits never leak
 * between tests.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class, SeaweedfsTestConfiguration.class,
		FakeRegionProvider.Configuration.class, FakeOrderClient.Configuration.class })
public abstract class EndpointTestSupport {

	protected static final String API = "/api/v1/identity";

	protected static final String PASSWORD = "Secret123";

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected FakeRegionProvider regions;

	@Autowired
	protected FakeOrderClient orders;

	@Autowired
	protected RoleAssignments roleAssignments;

	@Autowired
	protected UserRepository users;

	@Autowired
	protected TransactionTemplate tx;

	protected String clientIp;

	@BeforeEach
	void freshClientAndClock() {
		clock.reset();
		regions.setAvailable(true);
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

	protected ResultActions registerSeller(String email, String sellerName, String contactPhone) throws Exception {
		return postJson("/register-seller", json("email", email, "sellerName", sellerName, "fullName", "Tran Thi B",
				"contactPhone", contactPhone, "password", PASSWORD, "confirmPassword", PASSWORD));
	}

	protected ResultActions login(String email, String password) throws Exception {
		return postJson("/login", json("email", email, "password", password));
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

	/** Registers a seller (PENDING, no role), logs in and returns the session. */
	protected Session pendingSeller() throws Exception {
		String email = uniqueEmail();
		String body = registerSeller(email, "Shop " + UUID.randomUUID(), uniquePhone()).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return new Session(UUID.fromString(read(body, "$.data.userId")), email,
				read(loginBody(email), "$.data.accessToken"));
	}

	/** A seller approved as an admin would (APPROVED + SELLER role), logged in afterwards. */
	protected Session approvedSeller() throws Exception {
		Session pending = pendingSeller();
		approveSeller(pending.userId());
		return new Session(pending.userId(), pending.email(), read(loginBody(pending.email()), "$.data.accessToken"));
	}

	/**
	 * A registered user (who is also a customer) granted ADMIN, logged in afterwards. Admins are only made by the
	 * bootstrap in production; this is the test shortcut.
	 */
	protected Session admin() throws Exception {
		Session user = customer();
		tx.executeWithoutResult(status -> roleAssignments.grant(users.findById(user.userId()).orElseThrow(),
				RoleCode.ADMIN, null));
		return new Session(user.userId(), user.email(), read(loginBody(user.email()), "$.data.accessToken"));
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

	/** Marks the user's Seller profile APPROVED and grants SELLER, like an admin approval. */
	protected void approveSeller(UUID sellerId) {
		UUID reviewer = jdbc.queryForObject("""
				insert into users (email, auth_method, password_hash, full_name) values (?, 'LOCAL', 'x', 'Reviewer')
				returning user_id""", UUID.class, uniqueEmail());
		jdbc.update("""
				update sellers set approval_status = 'APPROVED', reviewed_at = now(), reviewed_by_user_id = ?
				where seller_id = ?""", reviewer, sellerId);
		tx.executeWithoutResult(status -> roleAssignments.grant(users.findById(sellerId).orElseThrow(), RoleCode.SELLER,
				reviewer));
	}

	protected UUID insertGoogleUser(String email) {
		return jdbc.queryForObject("""
				insert into users (email, auth_method, google_subject, full_name)
				values (?, 'GOOGLE', ?, 'Google User') returning user_id""", UUID.class, email, UUID.randomUUID().toString());
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
