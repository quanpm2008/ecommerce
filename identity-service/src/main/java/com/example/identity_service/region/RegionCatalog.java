package com.example.identity_service.region;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Region checks and names for the address book, on top of {@link RegionProvider}.
 */
@Component
@RequiredArgsConstructor
public class RegionCatalog {

	private final RegionProvider provider;

	/**
	 * Passes only if the source confirms the ward belongs to the province. A source that cannot answer gives 503,
	 * never a guess that the code is wrong.
	 */
	public void requireWardInProvince(String provinceCode, String wardCode) {
		boolean found = provider.wards(provinceCode).stream().anyMatch(ward -> ward.code().equals(wardCode));
		if (!found) {
			throw ApiException.forField(ErrorCode.INVALID_REGION, "wardCode", "NOT_IN_PROVINCE");
		}
	}

	/** Display names, or empty when the source is unavailable: listing addresses must not fail because of it. */
	public Optional<Names> names(String provinceCode, String wardCode) {
		try {
			String province = provider.provinces().stream().filter(p -> p.code().equals(provinceCode))
					.map(Province::name).findFirst().orElse(null);
			String ward = provider.wards(provinceCode).stream().filter(w -> w.code().equals(wardCode))
					.map(Ward::name).findFirst().orElse(null);
			return Optional.of(new Names(province, ward));
		}
		catch (ApiException ex) {
			return Optional.empty();
		}
	}

	public record Names(String provinceName, String wardName) {
	}

}
