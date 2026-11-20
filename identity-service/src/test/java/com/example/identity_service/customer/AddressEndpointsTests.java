package com.example.identity_service.customer;

import com.example.identity_service.EndpointTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static com.example.identity_service.TestRegions.BA_DINH;
import static com.example.identity_service.TestRegions.HANOI;
import static com.example.identity_service.TestRegions.HCMC;
import static com.example.identity_service.TestRegions.NGOC_HA;
import static com.example.identity_service.TestRegions.SAI_GON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /customers/me/addresses} (API §6.1, DB §3.1.4, UC-A06, checklist section 4). Regions come from
 * {@code FakeRegionProvider}.
 */
class AddressEndpointsTests extends EndpointTestSupport {

	private static final String PATH = "/customers/me/addresses";

	@Test
	void createdAddressCarriesRegionNamesAndIsTheDefaultWhenFirst() throws Exception {
		Session customer = customer();
		String phone = uniquePhone();

		call(HttpMethod.POST, PATH, address("Le Van D", "+84" + phone.substring(1), HANOI, BA_DINH),
				customer.accessToken())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.recipientPhone").value(phone))
				.andExpect(jsonPath("$.data.provinceName").value("Thành phố Hà Nội"))
				.andExpect(jsonPath("$.data.wardName").value("Phường Ba Đình"))
				.andExpect(jsonPath("$.data.isDefault").value(true))
				.andExpect(jsonPath("$.data.lastUsedAt").isEmpty());
	}

	@Test
	void listShowsOnlyOwnAddressesWithTheLastUsedOneFirst() throws Exception {
		Session customer = customer();
		String first = create(customer, "First", HANOI, BA_DINH);
		String second = create(customer, "Second", HANOI, NGOC_HA);
		String third = create(customer, "Third", HCMC, SAI_GON);
		create(customer(), "Someone else", HANOI, BA_DINH);
		jdbc.update("update customer_addresses set last_used_at = now() where address_id = ?", UUID.fromString(second));

		call(HttpMethod.GET, PATH, null, customer.accessToken()).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].addressId", contains(second, first, third)))
				.andExpect(jsonPath("$.data[*].isDefault", contains(true, false, false)))
				.andExpect(jsonPath("$.meta.maxAddresses").value(3));
	}

	@Test
	void theAddressBookHoldsAtMostThreeAddressesAndADeleteFreesAPlace() throws Exception {
		Session customer = customer();
		String first = create(customer, "Recipient 1", HANOI, BA_DINH);
		create(customer, "Recipient 2", HANOI, NGOC_HA);
		create(customer, "Recipient 3", HCMC, SAI_GON);

		call(HttpMethod.GET, PATH, null, customer.accessToken()).andExpect(jsonPath("$.meta.maxAddresses").value(3));
		call(HttpMethod.POST, PATH, address("Fourth", uniquePhone(), HANOI, BA_DINH), customer.accessToken())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("ADDRESS_LIMIT_EXCEEDED"));
		call(HttpMethod.DELETE, PATH + "/" + first, null, customer.accessToken()).andExpect(status().isNoContent());
		call(HttpMethod.POST, PATH, address("Fourth", uniquePhone(), HANOI, BA_DINH), customer.accessToken())
				.andExpect(status().isCreated());
	}

	@Test
	void concurrentAddsNeverPassTheLimit() throws Exception {
		Session customer = customer();
		var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
		try {
			var results = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
			for (int i = 0; i < 6; i++) {
				int n = i;
				results.add(pool.submit(() -> call(HttpMethod.POST, PATH, address("Race " + n, uniquePhone(), HANOI,
						BA_DINH), customer.accessToken()).andReturn().getResponse().getStatus()));
			}
			for (var result : results) {
				result.get();
			}
		}
		finally {
			pool.shutdown();
		}
		assertThat(jdbc.queryForObject("select count(*) from customer_addresses where customer_id = ?",
				Integer.class, customer.userId())).isEqualTo(3);
	}

	@Test
	void wardMustBelongToTheProvince() throws Exception {
		Session customer = customer();

		call(HttpMethod.POST, PATH, address("Le Van D", uniquePhone(), HCMC, BA_DINH), customer.accessToken())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("INVALID_REGION"));
		call(HttpMethod.POST, PATH, address("Le Van D", uniquePhone(), "99", BA_DINH), customer.accessToken())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("INVALID_REGION"));
	}

	@Test
	void unavailableRegionSeedIsAServiceErrorNotAnInvalidAddress() throws Throwable {
		Session customer = customer();
		String existing = create(customer, "Before outage", HANOI, BA_DINH);

		withoutRegionSeed(() -> {
			call(HttpMethod.POST, PATH, address("Le Van D", uniquePhone(), HANOI, BA_DINH), customer.accessToken())
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.error.code").value("REGION_SOURCE_UNAVAILABLE"));
			call(HttpMethod.GET, PATH, null, customer.accessToken()).andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].addressId").value(existing))
					.andExpect(jsonPath("$.data[0].provinceName").isEmpty());
		});
	}

	@Test
	void updateChangesTheAddress() throws Exception {
		Session customer = customer();
		String id = create(customer, "Old", HANOI, BA_DINH);

		call(HttpMethod.PUT, PATH + "/" + id, address("New", uniquePhone(), HCMC, SAI_GON), customer.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.recipientName").value("New"))
				.andExpect(jsonPath("$.data.wardName").value("Phường Sài Gòn"));
	}

	@Test
	void anotherCustomersAddressIsNotFound() throws Exception {
		Session owner = customer();
		String id = create(owner, "Owner", HANOI, BA_DINH);
		Session intruder = customer();

		call(HttpMethod.PUT, PATH + "/" + id, address("Hijack", uniquePhone(), HANOI, BA_DINH), intruder.accessToken())
				.andExpect(status().isNotFound());
		call(HttpMethod.DELETE, PATH + "/" + id, null, intruder.accessToken()).andExpect(status().isNotFound());
		call(HttpMethod.GET, PATH, null, owner.accessToken()).andExpect(jsonPath("$.data[0].recipientName").value("Owner"));
	}

	@Test
	void deletingRemovesTheAddressEvenOneAnOrderUsed() throws Exception {
		Session customer = customer();
		String unused = create(customer, "Unused", HANOI, BA_DINH);
		String used = create(customer, "Used", HANOI, NGOC_HA);
		jdbc.update("update customer_addresses set last_used_at = now() where address_id = ?", UUID.fromString(used));

		call(HttpMethod.DELETE, PATH + "/" + unused, null, customer.accessToken()).andExpect(status().isNoContent());
		call(HttpMethod.DELETE, PATH + "/" + used, null, customer.accessToken()).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("select count(*) from customer_addresses where customer_id = ?", Integer.class,
				customer.userId())).isZero();
		call(HttpMethod.GET, PATH, null, customer.accessToken()).andExpect(jsonPath("$.data", hasSize(0)));
		call(HttpMethod.DELETE, PATH + "/" + used, null, customer.accessToken()).andExpect(status().isNotFound());
	}

	@Test
	void addressBookNeedsACustomerProfile() throws Exception {
		call(HttpMethod.GET, PATH, null, approvedSeller().accessToken()).andExpect(status().isForbidden());
	}

	@Test
	void aBannedCustomerHasNoAddressBook() throws Exception {
		Session customer = customer();
		String id = create(customer, "Before ban", HANOI, BA_DINH);
		jdbc.update("""
				update customers set status = 'BANNED', ban_reason = 'Khác: Fraud', ban_mode = 'CUSTOMER_SCOPE',
				has_paid_order_before_ban = false where customer_id = ?""", customer.userId());

		call(HttpMethod.GET, PATH, null, customer.accessToken()).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ACCOUNT_BANNED"));
		call(HttpMethod.POST, PATH, address("After ban", uniquePhone(), HANOI, BA_DINH), customer.accessToken())
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_BANNED"));
		call(HttpMethod.DELETE, PATH + "/" + id, null, customer.accessToken()).andExpect(status().isForbidden());
	}

	@Test
	void invalidRecipientPhoneIsAValidationError() throws Exception {
		call(HttpMethod.POST, PATH, address("Le Van D", "12345", HANOI, BA_DINH), customer().accessToken())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("recipientPhone"));
	}

	private String create(Session customer, String recipientName, String provinceCode, String wardCode)
			throws Exception {
		return read(body(call(HttpMethod.POST, PATH, address(recipientName, uniquePhone(), provinceCode, wardCode),
				customer.accessToken()).andExpect(status().isCreated())), "$.data.addressId");
	}

	private static String address(String recipientName, String phone, String provinceCode, String wardCode) {
		return json("recipientName", recipientName, "recipientPhone", phone, "provinceCode", provinceCode,
				"wardCode", wardCode, "addressDetail", "12 Tran Phu");
	}

}
