package com.example.identity_service.region;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Placeholder until the HTTP provider is built (step 5): every lookup is {@code REGION_SOURCE_UNAVAILABLE}, as the
 * spec requires when no source is configured.
 */
@Component
class UnconfiguredRegionProvider implements RegionProvider {

	@Override
	public List<Province> provinces() {
		throw new ApiException(ErrorCode.REGION_SOURCE_UNAVAILABLE);
	}

	@Override
	public List<Ward> wards(String provinceCode) {
		throw new ApiException(ErrorCode.REGION_SOURCE_UNAVAILABLE);
	}

}
