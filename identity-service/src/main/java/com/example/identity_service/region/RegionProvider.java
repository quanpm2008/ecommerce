package com.example.identity_service.region;

import java.util.List;

/**
 * The external two-level administrative region source (API §6.1 "Nguồn dữ liệu địa giới", P-26): provinces and the
 * wards of one province, from one dataset version. There is deliberately no local table or static fallback.
 * Implementations call the source on every lookup ({@link RegionCatalog} caches) and throw
 * {@code ApiException(REGION_SOURCE_UNAVAILABLE)} when it is not configured or does not answer, and
 * {@code ApiException(INVALID_REGION)} only when the source confirms that a province code does not exist.
 */
public interface RegionProvider {

	List<Province> provinces();

	List<Ward> wards(String provinceCode);

}
