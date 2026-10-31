package com.example.identity_service.region;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.List;
import java.util.function.Supplier;

/**
 * {@link RegionProvider} over the two-level dataset of provinces.open-api.vn v2 ({@code GET /p/} and
 * {@code GET /p/{code}?depth=2}). The upstream uses integer codes; they are mapped to the official zero-padded
 * strings (province {@code 01}, ward {@code 00004}). No caching here: {@link RegionCatalog} caches.
 */
@Slf4j
@Component
class OpenApiVnRegionProvider implements RegionProvider {

	private final RegionProperties properties;

	private final RestClient http;

	OpenApiVnRegionProvider(RegionProperties properties) {
		this.properties = properties;
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
		requests.setReadTimeout(properties.timeout());
		this.http = RestClient.builder().requestFactory(requests).build();
	}

	@Override
	public List<Province> provinces() {
		UpstreamProvince[] provinces = fetch(() -> http.get().uri(baseUrl() + "/p/").retrieve()
				.body(UpstreamProvince[].class));
		return List.of(provinces).stream().map(p -> new Province(provinceCode(p.code()), p.name())).toList();
	}

	@Override
	public List<Ward> wards(String provinceCode) {
		if (!provinceCode.matches("\\d{1,3}")) {
			throw unknownProvince();
		}
		UpstreamProvince province = fetch(() -> {
			try {
				return http.get().uri(baseUrl() + "/p/{code}?depth=2", Integer.parseInt(provinceCode)).retrieve()
						.body(UpstreamProvince.class);
			}
			catch (HttpClientErrorException.NotFound ex) {
				throw unknownProvince();
			}
		});
		List<UpstreamWard> wards = province.wards() == null ? List.of() : province.wards();
		String code = provinceCode(province.code());
		return wards.stream().map(w -> new Ward(wardCode(w.code()), w.name(), code)).toList();
	}

	private String baseUrl() {
		if (!properties.configured()) {
			throw new ApiException(ErrorCode.REGION_SOURCE_UNAVAILABLE, "Region source is not configured");
		}
		return properties.baseUrl().replaceAll("/+$", "");
	}

	private <T> T fetch(Supplier<T> call) {
		try {
			T body = call.get();
			if (body == null) {
				throw new RestClientException("Empty response");
			}
			return body;
		}
		catch (RestClientException ex) {
			log.warn("Region source failed: {}", ex.getMessage());
			throw new ApiException(ErrorCode.REGION_SOURCE_UNAVAILABLE);
		}
	}

	private static ApiException unknownProvince() {
		return ApiException.forField(ErrorCode.INVALID_REGION, "provinceCode", "UNKNOWN_PROVINCE");
	}

	private static String provinceCode(int code) {
		return "%02d".formatted(code);
	}

	private static String wardCode(int code) {
		return "%05d".formatted(code);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record UpstreamProvince(int code, String name, List<UpstreamWard> wards) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record UpstreamWard(int code, String name) {
	}

}
