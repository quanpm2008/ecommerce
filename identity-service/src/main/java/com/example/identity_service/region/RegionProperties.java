package com.example.identity_service.region;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code identity.region.*}: the external region source (API §6.1 "Nguồn dữ liệu địa giới"). Only the server picks
 * the upstream; a blank {@code baseUrl} means "not configured" and every lookup is 503. {@code datasetVersion} is
 * returned as {@code meta.sourceVersion}, because the provider does not publish one.
 */
@ConfigurationProperties("identity.region")
public record RegionProperties(String baseUrl, String datasetVersion, Duration timeout, Duration cacheTtl) {

	public boolean configured() {
		return baseUrl != null && !baseUrl.isBlank();
	}

}
