package com.example.identity_service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Database-level rules from DB §3.1, checked with plain SQL so they hold whatever the application code does. A
 * failed statement aborts the PostgreSQL transaction, so each test ends with at most one expected violation.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Import(TestcontainersConfiguration.class)
@Transactional
class SchemaConstraintsTests {

	@Autowired
	JdbcTemplate jdbc;

	// --- users ---

	@Test
	void newUserDefaultsToActiveWithAuthzVersionOne() {
		UUID userId = insertLocalUser("defaults@example.test", null);

		Map<String, Object> row = jdbc.queryForMap("select status, authz_version from users where user_id = ?", userId);
		assertThat(row).containsEntry("status", "ACTIVE").containsEntry("authz_version", 1L);
	}

	@Test
	void emailMustBeStoredTrimmedAndLowercase() {
		assertViolation("ck_users_email_normalized", () -> insertLocalUser(" User@Example.test", null));
	}

	@Test
	void emailIsUnique() {
		insertLocalUser("same@example.test", null);

		assertViolation("uq_users_email", () -> insertLocalUser("same@example.test", null));
	}

	@Test
	void phoneNumberIsUniqueOnlyWhenPresent() {
		insertLocalUser("no-phone-1@example.test", null);
		insertLocalUser("no-phone-2@example.test", null);
		insertLocalUser("phone-1@example.test", "0912345678");

		assertViolation("uq_users_phone_number", () -> insertLocalUser("phone-2@example.test", "0912345678"));
	}

	@Test
	void phoneNumberMustBeNormalized() {
		assertViolation("ck_users_phone_number", () -> insertLocalUser("intl@example.test", "+84912345678"));
	}

	@Test
	void googleSubjectIsUniqueWhenPresent() {
		insertUser("g1@example.test", "GOOGLE", null, "google-sub", "ACTIVE", null);

		assertViolation("uq_users_google_subject",
				() -> insertUser("g2@example.test", "GOOGLE", null, "google-sub", "ACTIVE", null));
	}

	@ParameterizedTest
	@CsvSource(nullValues = "null", value = {
			"LOCAL, null, null",
			"LOCAL, hash, google-sub",
			"GOOGLE, null, null",
			"GOOGLE, hash, google-sub" })
	void authMethodDecidesWhichCredentialIsStored(String authMethod, String passwordHash, String googleSubject) {
		assertViolation("ck_users_auth_method",
				() -> insertUser("cred@example.test", authMethod, passwordHash, googleSubject, "ACTIVE", null));
	}

	@ParameterizedTest
	@CsvSource(nullValues = "null", value = { "DISABLED, null", "ACTIVE, Fraud" })
	void disabledReasonIsPresentExactlyWhenDisabled(String status, String disabledReason) {
		assertViolation("ck_users_disabled_reason",
				() -> insertUser("status@example.test", "LOCAL", "hash", null, status, disabledReason));
	}

	// --- customers and sellers share the user PK ---

	@ParameterizedTest
	@CsvSource({ "customers, customer_id", "sellers, seller_id" })
	void profileIdMustBeAnExistingUser(String table, String idColumn) {
		assertViolation(table + "_" + idColumn + "_fkey", () -> insertProfile(table, UUID.randomUUID()));
	}

	@ParameterizedTest
	@CsvSource({ "customers, customer_id", "sellers, seller_id" })
	void userWithAProfileCannotBeDeleted(String table, String idColumn) {
		UUID userId = insertLocalUser("restrict@example.test", null);
		insertProfile(table, userId);

		assertViolation(table + "_" + idColumn + "_fkey", () -> jdbc.update("delete from users where user_id = ?", userId));
	}

	@Test
	void oneUserCanHaveBothProfiles() {
		UUID userId = insertLocalUser("both@example.test", null);
		insertProfile("customers", userId);
		insertProfile("sellers", userId);

		assertThat(jdbc.queryForObject("""
				select count(*) from customers c join sellers s on s.seller_id = c.customer_id
				where c.customer_id = ?""", Integer.class, userId)).isEqualTo(1);
	}

	@Test
	void bannedCustomerNeedsReasonAndBanPolicySnapshot() {
		UUID userId = insertLocalUser("banned-customer@example.test", null);

		assertViolation("ck_customers_ban_fields", () -> jdbc.update(
				"insert into customers (customer_id, status, ban_reason) values (?, 'BANNED', 'Fraud')", userId));
	}

	@Test
	void bannedSellerNeedsReason() {
		UUID userId = insertLocalUser("banned-seller@example.test", null);

		assertViolation("ck_sellers_ban_reason", () -> jdbc.update(
				"insert into sellers (seller_id, seller_name, contact_phone, status) values (?, 'Shop', '0900000001', 'BANNED')",
				userId));
	}

	@Test
	void rejectedSellerNeedsReason() {
		UUID adminId = insertLocalUser("reviewer@example.test", null);
		UUID userId = insertLocalUser("rejected@example.test", null);

		assertViolation("ck_sellers_rejection_reason",
				() -> insertSeller(userId, "Shop", "0900000001", "REJECTED", null, adminId));
	}

	@Test
	void sellerCannotReviewOwnApplication() {
		UUID userId = insertLocalUser("self-review@example.test", null);

		assertViolation("ck_sellers_no_self_review",
				() -> insertSeller(userId, "Shop", "0900000001", "APPROVED", null, userId));
	}

	@Test
	void sellerNameIsUniqueAfterNormalizationAmongPendingAndApproved() {
		insertSeller(insertLocalUser("shop-1@example.test", null), "Shop Áo Xinh", "0900000001", "PENDING", null, null);

		assertViolation("uq_sellers_seller_name_active", () -> insertSeller(insertLocalUser("shop-2@example.test", null),
				"  shop   áo XINH ", "0900000002", "PENDING", null, null));
	}

	@Test
	void contactPhoneIsUniqueAmongPendingAndApproved() {
		UUID adminId = insertLocalUser("reviewer@example.test", null);
		insertSeller(insertLocalUser("shop-1@example.test", null), "Shop One", "0900000001", "APPROVED", null, adminId);

		assertViolation("uq_sellers_contact_phone_active", () -> insertSeller(
				insertLocalUser("shop-2@example.test", null), "Shop Two", "0900000001", "PENDING", null, null));
	}

	@Test
	void rejectedSellerFreesNameAndContactPhone() {
		UUID adminId = insertLocalUser("reviewer@example.test", null);
		insertSeller(insertLocalUser("shop-1@example.test", null), "Shop Áo Xinh", "0900000001", "REJECTED",
				"Incomplete documents", adminId);

		insertSeller(insertLocalUser("shop-2@example.test", null), "Shop Áo Xinh", "0900000001", "PENDING", null, null);

		assertThat(jdbc.queryForObject("select count(*) from sellers where seller_name = 'Shop Áo Xinh'", Integer.class))
				.isEqualTo(2);
	}

	// --- addresses ---

	@Test
	void addressMustBelongToACustomer() {
		UUID userWithoutCustomer = insertLocalUser("no-customer@example.test", null);

		assertViolation("customer_addresses_customer_id_fkey", () -> jdbc.update("""
				insert into customer_addresses (customer_id, recipient_name, recipient_phone, province_code, ward_code, address_detail)
				values (?, 'Recipient', '0900000001', '01', '00001', '1 Street')""", userWithoutCustomer));
	}

	private UUID insertLocalUser(String email, String phoneNumber) {
		return jdbc.queryForObject("""
				insert into users (email, auth_method, password_hash, full_name, phone_number)
				values (?, 'LOCAL', 'hash', 'Test User', ?) returning user_id""", UUID.class, email, phoneNumber);
	}

	private UUID insertUser(String email, String authMethod, String passwordHash, String googleSubject, String status,
			String disabledReason) {
		return jdbc.queryForObject("""
				insert into users (email, auth_method, password_hash, google_subject, full_name, status, disabled_reason)
				values (?, ?, ?, ?, 'Test User', ?, ?) returning user_id""", UUID.class, email, authMethod, passwordHash,
				googleSubject, status, disabledReason);
	}

	private void insertProfile(String table, UUID userId) {
		if (table.equals("customers")) {
			jdbc.update("insert into customers (customer_id) values (?)", userId);
		}
		else {
			insertSeller(userId, "Shop " + userId, "0900000009", "PENDING", null, null);
		}
	}

	/** {@code reviewedBy} doubles as the reviewed flag: non-null sets {@code reviewed_at} too. */
	private void insertSeller(UUID sellerId, String name, String phone, String approvalStatus, String rejectionReason,
			UUID reviewedBy) {
		jdbc.update("""
				insert into sellers (seller_id, seller_name, contact_phone, approval_status, rejection_reason,
				                     reviewed_at, reviewed_by_user_id)
				values (?, ?, ?, ?, ?, case when ?::uuid is null then null else now() end, ?)""", sellerId, name, phone,
				approvalStatus, rejectionReason, reviewedBy, reviewedBy);
	}

	private static void assertViolation(String constraint, Runnable statement) {
		assertThatThrownBy(statement::run).isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining(constraint);
	}

}
