package com.example.identity_service;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.region.Province;
import com.example.identity_service.region.RegionProvider;
import com.example.identity_service.region.Ward;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;

/**
 * A tiny two-level region dataset in place of the external source; {@link #setAvailable(boolean)} simulates an outage.
 */
public class FakeRegionProvider implements RegionProvider {

	public static final String HANOI = "01";

	public static final String BA_DINH = "00004";

	public static final String NGOC_HA = "00008";

	public static final String HCMC = "79";

	public static final String SAI_GON = "26734";

	private static final List<Province> PROVINCES = List.of(new Province(HANOI, "Thành phố Hà Nội"),
			new Province(HCMC, "Thành phố Hồ Chí Minh"));

	private static final Map<String, List<Ward>> WARDS = Map.of(
			HANOI, List.of(new Ward(BA_DINH, "Phường Ba Đình", HANOI), new Ward(NGOC_HA, "Phường Ngọc Hà", HANOI)),
			HCMC, List.of(new Ward(SAI_GON, "Phường Sài Gòn", HCMC)));

	private volatile boolean available = true;

	public void setAvailable(boolean available) {
		this.available = available;
	}

	@Override
	public List<Province> provinces() {
		requireAvailable();
		return PROVINCES;
	}

	@Override
	public List<Ward> wards(String provinceCode) {
		requireAvailable();
		List<Ward> wards = WARDS.get(provinceCode);
		if (wards == null) {
			throw ApiException.forField(ErrorCode.INVALID_REGION, "provinceCode", "UNKNOWN_PROVINCE");
		}
		return wards;
	}

	private void requireAvailable() {
		if (!available) {
			throw new ApiException(ErrorCode.REGION_SOURCE_UNAVAILABLE);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Configuration {

		@Bean
		@Primary
		FakeRegionProvider fakeRegionProvider() {
			return new FakeRegionProvider();
		}

	}

}
