package com.example.identity_service.auth;

import com.example.identity_service.MutableClock;
import com.example.identity_service.TestClockConfiguration;
import com.example.identity_service.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for the auth endpoint tests: one Spring context, fresh emails and phone numbers per test, and a
 * random client IP per test so per-IP rate limits never leak between tests.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class })
abstract class AuthEndpointTestSupport {

	static final String PASSWORD = "Secret123";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	String clientIp;

	@BeforeEach
	void freshClientAndClock() {
		clock.reset();
		clientIp = randomIp();
	}

	static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.test";
	}

	static String uniquePhone() {
		return "09" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
	}

	static String randomIp() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
	}

	ResultActions postJson(String path, String json) throws Exception {
		return mvc.perform(withIp(post(AuthController.PATH + path)).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	ResultActions postJson(String path, String json, String accessToken) throws Exception {
		return mvc.perform(withIp(post(AuthController.PATH + path)).contentType(MediaType.APPLICATION_JSON)
				.content(json).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	ResultActions changePassword(String accessToken, String current, String next, String confirm) throws Exception {
		return mvc.perform(put("/api/v1/identity/users/me/password").contentType(MediaType.APPLICATION_JSON)
				.content(json("currentPassword", current, "newPassword", next, "confirmPassword", confirm))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	ResultActions register(String email, String phone, String password) throws Exception {
		return postJson("/register", json("email", email, "fullName", "Nguyen Van An", "phoneNumber", phone,
				"password", password, "confirmPassword", password));
	}

	/** Registers a customer and returns its user id. */
	String registerCustomer(String email) throws Exception {
		String body = register(email, uniquePhone(), PASSWORD).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.userId");
	}

	ResultActions registerSeller(String email, String sellerName, String contactPhone) throws Exception {
		return postJson("/register-seller", json("email", email, "sellerName", sellerName, "fullName", "Tran Thi B",
				"contactPhone", contactPhone, "password", PASSWORD, "confirmPassword", PASSWORD));
	}

	ResultActions login(String email, String password) throws Exception {
		return postJson("/login", json("email", email, "password", password));
	}

	/** Logs in and returns the response body. */
	String loginBody(String email) throws Exception {
		return login(email, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	ResultActions refresh(String refreshToken) throws Exception {
		return postJson("/refresh", json("refreshToken", refreshToken));
	}

	UUID insertGoogleUser(String email) {
		return jdbc.queryForObject("""
				insert into users (email, auth_method, google_subject, full_name)
				values (?, 'GOOGLE', ?, 'Google User') returning user_id""", UUID.class, email, UUID.randomUUID().toString());
	}

	void disableUser(String email) {
		jdbc.update("update users set status = 'DISABLED', disabled_reason = 'Fraud' where email = ?", email);
	}

	/** A flat JSON object from alternating keys and string values. */
	static String json(String... keysAndValues) {
		StringBuilder json = new StringBuilder("{");
		for (int i = 0; i < keysAndValues.length; i += 2) {
			json.append(i == 0 ? "" : ",").append('"').append(keysAndValues[i]).append("\":\"")
					.append(keysAndValues[i + 1].replace("\"", "\\\"")).append('"');
		}
		return json.append('}').toString();
	}

	static String read(String body, String path) {
		return JsonPath.read(body, path);
	}

	private MockHttpServletRequestBuilder withIp(MockHttpServletRequestBuilder request) {
		return request.with(r -> {
			r.setRemoteAddr(clientIp);
			return r;
		});
	}

}
