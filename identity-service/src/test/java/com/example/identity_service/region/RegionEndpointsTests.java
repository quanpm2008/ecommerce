package com.example.identity_service.region;

import com.example.identity_service.EndpointTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Duration;

import static com.example.identity_service.FakeRegionProvider.BA_DINH;
import static com.example.identity_service.FakeRegionProvider.HANOI;
import static com.example.identity_service.FakeRegionProvider.HCMC;
import static com.example.identity_service.FakeRegionProvider.NGOC_HA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /regions/**} (API §6.1 "Nguồn dữ liệu địa giới", checklist section 5). The source is
 * {@code FakeRegionProvider}; the clock moves to test the 24 h cache.
 */
class RegionEndpointsTests extends EndpointTestSupport {

	@Test
	void provincesArePublicAndCarryTheSourceVersion() throws Exception {
		call(HttpMethod.GET, "/regions/provinces", null, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].code", contains(HANOI, HCMC)))
				.andExpect(jsonPath("$.data[0].name").value("Thành phố Hà Nội"))
				.andExpect(jsonPath("$.meta.sourceVersion").value("provinces-open-api-vn-v2"))
				.andExpect(jsonPath("$.meta.fetchedAt").isNotEmpty());
	}

	@Test
	void wardsOfAProvince() throws Exception {
		call(HttpMethod.GET, "/regions/provinces/" + HANOI + "/wards", null, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].code", contains(BA_DINH, NGOC_HA)))
				.andExpect(jsonPath("$.data[0].name").value("Phường Ba Đình"))
				.andExpect(jsonPath("$.data[0].provinceCode").value(HANOI))
				.andExpect(jsonPath("$.meta.sourceVersion").value("provinces-open-api-vn-v2"));
	}

	@Test
	void unknownProvinceIsAnInvalidRegion() throws Exception {
		call(HttpMethod.GET, "/regions/provinces/99/wards", null, null).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("INVALID_REGION"))
				.andExpect(jsonPath("$.error.details[0].field").value("provinceCode"));
	}

	@Test
	void aValidCacheAnswersDuringAnOutage() throws Exception {
		clock.advance(Duration.ofDays(30)); // past anything cached by other tests
		String first = read(body(call(HttpMethod.GET, "/regions/provinces", null, null).andExpect(status().isOk())),
				"$.meta.fetchedAt");
		clock.advance(Duration.ofHours(23));
		regions.setAvailable(false);

		String cached = read(body(call(HttpMethod.GET, "/regions/provinces", null, null).andExpect(status().isOk())),
				"$.meta.fetchedAt");
		assertThat(cached).isEqualTo(first);
	}

	@Test
	void outageWithAnExpiredCacheIsUnavailableNotAStaticList() throws Exception {
		call(HttpMethod.GET, "/regions/provinces", null, null).andExpect(status().isOk());
		clock.advance(Duration.ofHours(25));
		regions.setAvailable(false);

		call(HttpMethod.GET, "/regions/provinces", null, null).andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error.code").value("REGION_SOURCE_UNAVAILABLE"));
		call(HttpMethod.GET, "/regions/provinces/" + HANOI + "/wards", null, null)
				.andExpect(status().isServiceUnavailable());
	}

}
