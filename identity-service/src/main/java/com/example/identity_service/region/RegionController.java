package com.example.identity_service.region;

import com.example.identity_service.common.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public facade over the external region source (API §6.1, UC-A06): {@code meta { sourceVersion, fetchedAt }} says
 * which dataset answered and when it was fetched.
 */
@RestController
@RequestMapping(RegionController.PATH)
@RequiredArgsConstructor
public class RegionController {

	public static final String PATH = "/api/v1/identity/regions";

	private final RegionCatalog regions;

	@GetMapping("/provinces")
	ApiResponse<List<Province>> provinces() {
		RegionCatalog.Snapshot<List<Province>> provinces = regions.provinces();
		return ApiResponse.of(provinces.items(), provinces.meta());
	}

	@GetMapping("/provinces/{provinceCode}/wards")
	ApiResponse<List<Ward>> wards(@PathVariable String provinceCode) {
		RegionCatalog.Snapshot<List<Ward>> wards = regions.wards(provinceCode);
		return ApiResponse.of(wards.items(), wards.meta());
	}

}
