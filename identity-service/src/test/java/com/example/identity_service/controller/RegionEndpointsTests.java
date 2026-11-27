package com.example.identity_service.controller;

import com.example.identity_service.EndpointTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;


import static com.example.identity_service.TestRegions.BA_DINH;
import static com.example.identity_service.TestRegions.HANOI;
import static com.example.identity_service.TestRegions.HCMC;
import static com.example.identity_service.TestRegions.NGOC_HA;
import static com.example.identity_service.TestRegions.VERSION;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /regions/**} (API §6.1 "Nguồn dữ liệu địa giới", P-26): the dataset seeded by migration V8, read from the
 * database; nothing external.
 */
class RegionEndpointsTests extends EndpointTestSupport {

	@Test
	void provincesArePublicAndCarryTheSeedVersion() throws Exception {
		call(HttpMethod.GET, "/regions/provinces", null, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(34))
				.andExpect(jsonPath("$.data[0].code").value(HANOI))
				.andExpect(jsonPath("$.data[0].name").value("Thành phố Hà Nội"))
				.andExpect(jsonPath("$.data[*].code", hasItem(HCMC)))
				.andExpect(jsonPath("$.meta.sourceVersion").value(VERSION))
				.andExpect(jsonPath("$.meta.fetchedAt").value(clock.instant().toString()));
	}

	@Test
	void wardsOfAProvince() throws Exception {
		call(HttpMethod.GET, "/regions/provinces/" + HANOI + "/wards", null, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].code", hasItems(BA_DINH, NGOC_HA)))
				.andExpect(jsonPath("$.data[*].provinceCode", everyItem(is(HANOI))))
				.andExpect(jsonPath("$.data[?(@.code == '" + BA_DINH + "')].name", contains("Phường Ba Đình")))
				.andExpect(jsonPath("$.meta.sourceVersion").value(VERSION));
	}

	@Test
	void unknownProvinceIsAnInvalidRegion() throws Exception {
		call(HttpMethod.GET, "/regions/provinces/99/wards", null, null).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("INVALID_REGION"))
				.andExpect(jsonPath("$.error.details[0].field").value("provinceCode"));
	}

	@Test
	void anUnreadableSeedIsUnavailableNotAnInvalidRegion() throws Throwable {
		withoutRegionSeed(() -> {
			call(HttpMethod.GET, "/regions/provinces", null, null).andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.error.code").value("REGION_SOURCE_UNAVAILABLE"));
			call(HttpMethod.GET, "/regions/provinces/" + HANOI + "/wards", null, null)
					.andExpect(status().isServiceUnavailable());
			call(HttpMethod.GET, "/regions/provinces/99/wards", null, null).andExpect(status().isServiceUnavailable());
		});
	}

}
