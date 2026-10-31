package com.example.identity_service.region;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Cached region lookups on top of {@link RegionProvider}, for the region endpoints and the address book. A result is
 * kept for {@code identity.region.cache-ttl} (24 h proposed) and only used while it is still valid: once it expires
 * and the source does not answer, the lookup is 503, never stale or static data.
 */
@Component
@RequiredArgsConstructor
public class RegionCatalog {

	private static final String PROVINCES = "provinces";

	private final RegionProvider provider;

	private final RegionProperties properties;

	private final Clock clock;

	private final Map<String, Snapshot<?>> cache = new ConcurrentHashMap<>();

	public Snapshot<List<Province>> provinces() {
		return cached(PROVINCES, provider::provinces);
	}

	/** Wards of a province; a code missing from the source's province list is {@code INVALID_REGION}. */
	public Snapshot<List<Ward>> wards(String provinceCode) {
		boolean known = provinces().items().stream().anyMatch(p -> p.code().equals(provinceCode));
		if (!known) {
			throw ApiException.forField(ErrorCode.INVALID_REGION, "provinceCode", "UNKNOWN_PROVINCE");
		}
		return cached("wards:" + provinceCode, () -> provider.wards(provinceCode));
	}

	/**
	 * Passes only if the source confirms the ward belongs to the province. A source that cannot answer gives 503,
	 * never a guess that the code is wrong.
	 */
	public void requireWardInProvince(String provinceCode, String wardCode) {
		boolean found = wards(provinceCode).items().stream().anyMatch(ward -> ward.code().equals(wardCode));
		if (!found) {
			throw ApiException.forField(ErrorCode.INVALID_REGION, "wardCode", "NOT_IN_PROVINCE");
		}
	}

	/** Display names, or empty when the source is unavailable: listing addresses must not fail because of it. */
	public Optional<Names> names(String provinceCode, String wardCode) {
		try {
			String province = provinces().items().stream().filter(p -> p.code().equals(provinceCode))
					.map(Province::name).findFirst().orElse(null);
			String ward = wards(provinceCode).items().stream().filter(w -> w.code().equals(wardCode))
					.map(Ward::name).findFirst().orElse(null);
			return Optional.of(new Names(province, ward));
		}
		catch (ApiException ex) {
			return Optional.empty();
		}
	}

	@SuppressWarnings("unchecked")
	private <T> Snapshot<T> cached(String key, Supplier<T> fetch) {
		Instant now = clock.instant();
		Snapshot<T> hit = (Snapshot<T>) cache.get(key);
		// A fetch time in the future (clock stepped back) is not trusted either.
		if (hit != null && !now.isBefore(hit.fetchedAt()) && now.isBefore(hit.fetchedAt().plus(properties.cacheTtl()))) {
			return hit;
		}
		Snapshot<T> fresh = new Snapshot<>(fetch.get(), properties.datasetVersion(), now);
		cache.put(key, fresh);
		return fresh;
	}

	public record Snapshot<T>(T items, String sourceVersion, Instant fetchedAt) {

		public Meta meta() {
			return new Meta(sourceVersion, fetchedAt);
		}

	}

	public record Meta(String sourceVersion, Instant fetchedAt) {
	}

	public record Names(String provinceName, String wardName) {
	}

}
