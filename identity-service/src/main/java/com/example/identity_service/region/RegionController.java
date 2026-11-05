package com.example.identity_service.region;

import com.example.identity_service.common.openapi.ErrorCodes;
import com.example.identity_service.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.example.identity_service.common.error.ErrorCode.INVALID_REGION;
import static com.example.identity_service.common.error.ErrorCode.REGION_SOURCE_UNAVAILABLE;

/**
 * Public facade over the external region source (API §6.1, UC-A06): {@code meta { sourceVersion, fetchedAt }} says
 * which dataset answered and when it was fetched.
 */
@Tag(name = "Regions", description = "Provinces and wards of Vietnam's two-level administrative map, from an external "
		+ "source cached for 24 h")
@RestController
@RequestMapping(RegionController.PATH)
@RequiredArgsConstructor
public class RegionController {

	public static final String PATH = "/api/v1/identity/regions";

	private final RegionCatalog regions;

	@Operation(summary = "Provinces", description = "`meta { sourceVersion, fetchedAt }` says which dataset answered "
			+ "and when it was fetched.")
	@ErrorCodes(REGION_SOURCE_UNAVAILABLE)
	@GetMapping("/provinces")
	ApiResponse<List<Province>> provinces() {
		RegionCatalog.Snapshot<List<Province>> provinces = regions.provinces();
		return ApiResponse.of(provinces.items(), provinces.meta());
	}

	@Operation(summary = "Wards of a province", description = "Province codes are two digits (e.g. `01`), ward codes "
			+ "five (e.g. `00004`).")
	@ErrorCodes({ INVALID_REGION, REGION_SOURCE_UNAVAILABLE })
	@GetMapping("/provinces/{provinceCode}/wards")
	ApiResponse<List<Ward>> wards(@PathVariable String provinceCode) {
		RegionCatalog.Snapshot<List<Ward>> wards = regions.wards(provinceCode);
		return ApiResponse.of(wards.items(), wards.meta());
	}

}
