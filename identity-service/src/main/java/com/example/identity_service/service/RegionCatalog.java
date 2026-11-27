package com.example.identity_service.service;

import com.example.identity_service.dto.Province;
import com.example.identity_service.dto.Ward;
import com.example.identity_service.exception.ApiException;
import com.example.identity_service.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The administrative regions seeded in Identity's database (P-26, API §6.1 "Nguồn dữ liệu địa giới", migration V8):
 * provinces and the wards of a province, from one dataset version. The region endpoints, the address book and order
 * snapshots all read it; nothing calls an external source.
 * <ul>
 * <li>Seed missing or unreadable → 503 {@code REGION_SOURCE_UNAVAILABLE}, never a guess that a code is wrong.</li>
 * <li>422 {@code INVALID_REGION} only when the seed is there and confirms the code or relation is wrong.</li>
 * </ul>
 * {@code fetchedAt} in the meta is when this read happened; {@code sourceVersion} is the seed's version.
 */
@Component
@RequiredArgsConstructor
public class RegionCatalog {

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public Snapshot<List<Province>> provinces() {
		String version = version();
		List<Province> provinces = read(() -> jdbc.query(
				"select province_code, name from region_provinces order by province_code",
				(rs, row) -> new Province(rs.getString(1), rs.getString(2))));
		if (provinces.isEmpty()) {
			throw unavailable();
		}
		return new Snapshot<>(provinces, version, clock.instant());
	}

	/** Wards of a province; a province the seed does not know is {@code INVALID_REGION}. */
	public Snapshot<List<Ward>> wards(String provinceCode) {
		String version = version();
		boolean known = read(() -> jdbc.queryForObject(
				"select exists (select 1 from region_provinces where province_code = ?)", Boolean.class, provinceCode));
		if (!known) {
			throw ApiException.forField(ErrorCode.INVALID_REGION, "provinceCode", "UNKNOWN_PROVINCE");
		}
		List<Ward> wards = read(() -> jdbc.query(
				"select ward_code, name, province_code from region_wards where province_code = ? order by name",
				(rs, row) -> new Ward(rs.getString(1), rs.getString(2), rs.getString(3)), provinceCode));
		return new Snapshot<>(wards, version, clock.instant());
	}

	/** Passes only if the seed confirms the ward belongs to the province. */
	public void requireWardInProvince(String provinceCode, String wardCode) {
		requireNames(provinceCode, wardCode);
	}

	/** Display names, or empty when the seed cannot be read: listing addresses must not fail because of it. */
	public Optional<Names> names(String provinceCode, String wardCode) {
		try {
			return Optional.of(lookUpNames(provinceCode, wardCode));
		}
		catch (ApiException ex) {
			return Optional.empty();
		}
	}

	/**
	 * Both names for an address that must be complete (an order keeps them): 503 if the seed cannot be read, 422
	 * {@code INVALID_REGION} if the province is unknown or the ward is not in it.
	 */
	public Names requireNames(String provinceCode, String wardCode) {
		Names names = lookUpNames(provinceCode, wardCode);
		if (names.provinceName() == null) {
			throw ApiException.forField(ErrorCode.INVALID_REGION, "provinceCode", "UNKNOWN_PROVINCE");
		}
		if (names.wardName() == null) {
			throw ApiException.forField(ErrorCode.INVALID_REGION, "wardCode", "NOT_IN_PROVINCE");
		}
		return names;
	}

	private Names lookUpNames(String provinceCode, String wardCode) {
		version();
		String province = read(() -> jdbc.query("select name from region_provinces where province_code = ?",
				rs -> rs.next() ? rs.getString(1) : null, provinceCode));
		String ward = province == null ? null : read(() -> jdbc.query(
				"select name from region_wards where ward_code = ? and province_code = ?",
				rs -> rs.next() ? rs.getString(1) : null, wardCode, provinceCode));
		return new Names(province, ward);
	}

	/** The loaded dataset's version; no dataset → 503. */
	private String version() {
		String version = read(() -> jdbc.query("select version from region_dataset order by loaded_at desc limit 1",
				rs -> rs.next() ? rs.getString(1) : null));
		if (version == null) {
			throw unavailable();
		}
		return version;
	}

	private static <T> T read(java.util.function.Supplier<T> query) {
		try {
			return query.get();
		}
		catch (DataAccessException ex) {
			throw unavailable();
		}
	}

	private static ApiException unavailable() {
		return new ApiException(ErrorCode.REGION_SOURCE_UNAVAILABLE);
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
