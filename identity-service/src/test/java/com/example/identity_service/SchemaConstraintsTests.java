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
	@CsvSource({ "customers, CUSTOMER", "sellers, SELLER" })
	void profileIdMustBeAnExistingUser(String table, String accountType) {
		assertViolation("needs a " + accountType + " account, not none", () -> insertProfile(table, UUID.randomUUID()));
	}

	@ParameterizedTest
	@CsvSource({ "customers, customer_id, CUSTOMER", "sellers, seller_id, SELLER" })
	void userWithAProfileCannotBeDeleted(String table, String idColumn, String accountType) {
		UUID userId = insertLocalUser("restrict@example.test", null, accountType);
		insertProfile(table, userId);

		assertViolation(table + "_" + idColumn + "_fkey", () -> jdbc.update("delete from users where user_id = ?", userId));
	}

	// --- one account per role (V6) ---

	@ParameterizedTest
	@CsvSource({ "customers, SELLER, CUSTOMER", "customers, ADMIN, CUSTOMER", "sellers, CUSTOMER, SELLER",
			"sellers, ADMIN, SELLER" })
	void aProfileNeedsAnAccountOfItsOwnType(String table, String accountType, String needed) {
		UUID userId = insertLocalUser("wrong-type@example.test", null, accountType);

		assertViolation("needs a " + needed + " account, not " + accountType, () -> insertProfile(table, userId));
	}

	@Test
	void accountTypeIsOneOfTheThree() {
		assertViolation("ck_users_account_type", () -> insertLocalUser("bad-type@example.test", null, "STAFF"));
	}

	@Test
	void accountTypeIsRequired() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into users (email, auth_method, password_hash, full_name) values ('no-type@example.test', 'LOCAL',
				'hash', 'Test User')""")).isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("account_type");
	}

	@Test
	void accountTypeNeverChanges() {
		UUID userId = insertLocalUser("fixed@example.test", null);

		assertViolation("account_type cannot change",
				() -> jdbc.update("update users set account_type = 'SELLER' where user_id = ?", userId));
	}

	@Test
	void googleSignInIsForCustomersOnly() {
		assertViolation("ck_users_google_customer", () -> jdbc.update("""
				insert into users (email, account_type, auth_method, google_subject, full_name)
				values ('g-seller@example.test', 'SELLER', 'GOOGLE', 'sub-1', 'Shop')"""));
	}

	@ParameterizedTest
	@CsvSource({ "CUSTOMER, SELLER", "SELLER, ADMIN", "ADMIN, CUSTOMER" })
	void theActiveRoleMustBeTheAccountsOwn(String accountType, String role) {
		UUID userId = insertLocalUser("role-type@example.test", null, accountType);

		assertViolation("does not match account type", () -> grantRole(userId, role));
	}

	@Test
	void anAccountHasAtMostOneActiveRole() {
		UUID userId = insertLocalUser("one-role@example.test", null, "CUSTOMER");
		grantRole(userId, "CUSTOMER");
		jdbc.update("update user_roles set revoked_at = now() where user_id = ?", userId);
		jdbc.update("update user_roles set revoked_at = null where user_id = ?", userId);

		assertThat(jdbc.queryForObject("select count(*) from user_roles where user_id = ? and revoked_at is null",
				Integer.class, userId)).isEqualTo(1);
		assertThat(jdbc.queryForList("select indexdef from pg_indexes where indexname = 'uq_user_roles_one_active'",
				String.class)).singleElement().asString().contains("UNIQUE").contains("(user_id)")
				.contains("revoked_at IS NULL");
	}

	@Test
	void bannedCustomerNeedsReasonAndBanPolicySnapshot() {
		UUID userId = insertLocalUser("banned-customer@example.test", null);

		assertViolation("ck_customers_ban_fields", () -> jdbc.update(
				"insert into customers (customer_id, status, ban_reason) values (?, 'BANNED', 'Fraud')", userId));
	}

	@Test
	void bannedSellerNeedsReason() {
		UUID userId = insertSellerUser("banned-seller@example.test");

		assertViolation("ck_sellers_ban_reason", () -> jdbc.update(
				"insert into sellers (seller_id, seller_name, contact_phone, status) values (?, 'Shop', '0900000001', 'BANNED')",
				userId));
	}

	@Test
	void rejectedSellerNeedsReason() {
		UUID adminId = insertAdmin("reviewer@example.test");
		UUID userId = insertSellerUser("rejected@example.test");

		assertViolation("ck_sellers_rejection_reason",
				() -> insertSeller(userId, "Shop", "0900000001", "REJECTED", null, adminId));
	}

	@Test
	void sellerCannotReviewOwnApplication() {
		UUID userId = insertSellerUser("self-review@example.test");

		assertViolation("ck_sellers_no_self_review",
				() -> insertSeller(userId, "Shop", "0900000001", "APPROVED", null, userId));
	}

	@Test
	void sellerNameIsUniqueAfterNormalizationAmongPendingAndApproved() {
		insertSeller(insertSellerUser("shop-1@example.test"), "Shop Áo Xinh", "0900000001", "PENDING", null, null);

		assertViolation("uq_sellers_seller_name_active", () -> insertSeller(insertSellerUser("shop-2@example.test"),
				"  shop   áo XINH ", "0900000002", "PENDING", null, null));
	}

	@Test
	void contactPhoneIsUniqueAmongPendingAndApproved() {
		UUID adminId = insertAdmin("reviewer@example.test");
		insertSeller(insertSellerUser("shop-1@example.test"), "Shop One", "0900000001", "APPROVED", null, adminId);

		assertViolation("uq_sellers_contact_phone_active", () -> insertSeller(
				insertSellerUser("shop-2@example.test"), "Shop Two", "0900000001", "PENDING", null, null));
	}

	@Test
	void rejectedSellerFreesNameAndContactPhone() {
		UUID adminId = insertAdmin("reviewer@example.test");
		insertSeller(insertSellerUser("shop-1@example.test"), "Shop Áo Xinh", "0900000001", "REJECTED",
				"Incomplete documents", adminId);

		insertSeller(insertSellerUser("shop-2@example.test"), "Shop Áo Xinh", "0900000001", "PENDING", null, null);

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
		return insertLocalUser(email, phoneNumber, "CUSTOMER");
	}

	private UUID insertLocalUser(String email, String phoneNumber, String accountType) {
		return jdbc.queryForObject("""
				insert into users (email, account_type, auth_method, password_hash, full_name, phone_number)
				values (?, ?, 'LOCAL', 'hash', 'Test User', ?) returning user_id""", UUID.class, email, accountType,
				phoneNumber);
	}

	private UUID insertSellerUser(String email) {
		return insertLocalUser(email, null, "SELLER");
	}

	private UUID insertAdmin(String email) {
		return insertLocalUser(email, null, "ADMIN");
	}

	private UUID insertUser(String email, String authMethod, String passwordHash, String googleSubject, String status,
			String disabledReason) {
		return jdbc.queryForObject("""
				insert into users (email, account_type, auth_method, password_hash, google_subject, full_name, status,
				                   disabled_reason)
				values (?, 'CUSTOMER', ?, ?, ?, 'Test User', ?, ?) returning user_id""", UUID.class, email, authMethod,
				passwordHash, googleSubject, status, disabledReason);
	}

	private void grantRole(UUID userId, String role) {
		jdbc.update("insert into user_roles (user_id, role_id) select ?, role_id from roles where code = ?", userId,
				role);
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
