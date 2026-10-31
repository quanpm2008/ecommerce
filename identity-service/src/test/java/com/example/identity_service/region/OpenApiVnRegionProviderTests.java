package com.example.identity_service.region;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The provinces.open-api.vn v2 adapter against a local stand-in for the upstream: code mapping, the 404 that confirms
 * an unknown province, and failures that must become 503.
 */
class OpenApiVnRegionProviderTests {

	private static final String PROVINCES = """
			[{"name":"Thành phố Hà Nội","code":1,"division_type":"thành phố trung ương","wards":[]},
			 {"name":"Thành phố Hồ Chí Minh","code":79,"division_type":"thành phố trung ương","wards":[]}]""";

	private static final String HANOI = """
			{"name":"Thành phố Hà Nội","code":1,"wards":[
			 {"name":"Phường Ba Đình","code":4,"division_type":"phường","province_code":1},
			 {"name":"Phường Ngọc Hà","code":8,"division_type":"phường","province_code":1}]}""";

	private final Map<String, Response> routes = new ConcurrentHashMap<>();

	private HttpServer upstream;

	@BeforeEach
	void startUpstream() throws IOException {
		upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		upstream.createContext("/", this::answer);
		upstream.start();
	}

	@AfterEach
	void stopUpstream() {
		upstream.stop(0);
	}

	@Test
	void provincesAndWardsGetZeroPaddedStringCodes() {
		routes.put("/api/v2/p/", new Response(200, PROVINCES, Duration.ZERO));
		routes.put("/api/v2/p/1?depth=2", new Response(200, HANOI, Duration.ZERO));
		OpenApiVnRegionProvider provider = provider(baseUrl() + "/", Duration.ofSeconds(2));

		assertThat(provider.provinces()).containsExactly(new Province("01", "Thành phố Hà Nội"),
				new Province("79", "Thành phố Hồ Chí Minh"));
		assertThat(provider.wards("01")).containsExactly(new Ward("00004", "Phường Ba Đình", "01"),
				new Ward("00008", "Phường Ngọc Hà", "01"));
	}

	@Test
	void notFoundFromTheSourceConfirmsAnUnknownProvince() {
		routes.put("/api/v2/p/99?depth=2", new Response(404,
				"{\"type\":\"province-not-exist\",\"status\":404}", Duration.ZERO));

		assertCode(() -> provider(baseUrl(), Duration.ofSeconds(2)).wards("99"), ErrorCode.INVALID_REGION);
	}

	@Test
	void serverErrorsAndMisconfiguredPathsAreUnavailable() {
		routes.put("/api/v2/p/", new Response(500, "oops", Duration.ZERO));
		OpenApiVnRegionProvider provider = provider(baseUrl(), Duration.ofSeconds(2));

		assertCode(provider::provinces, ErrorCode.REGION_SOURCE_UNAVAILABLE);
		assertCode(() -> provider(baseUrl() + "/wrong", Duration.ofSeconds(2)).provinces(),
				ErrorCode.REGION_SOURCE_UNAVAILABLE);
	}

	@Test
	void slowSourceTimesOutAsUnavailable() {
		routes.put("/api/v2/p/", new Response(200, PROVINCES, Duration.ofSeconds(2)));

		assertCode(() -> provider(baseUrl(), Duration.ofMillis(300)).provinces(), ErrorCode.REGION_SOURCE_UNAVAILABLE);
	}

	@Test
	void unconfiguredSourceIsUnavailable() {
		assertCode(() -> provider("", Duration.ofSeconds(2)).provinces(), ErrorCode.REGION_SOURCE_UNAVAILABLE);
		assertCode(() -> provider(null, Duration.ofSeconds(2)).wards("01"), ErrorCode.REGION_SOURCE_UNAVAILABLE);
	}

	private OpenApiVnRegionProvider provider(String baseUrl, Duration timeout) {
		return new OpenApiVnRegionProvider(new RegionProperties(baseUrl, "test", timeout, Duration.ofHours(24)));
	}

	private String baseUrl() {
		return "http://127.0.0.1:" + upstream.getAddress().getPort() + "/api/v2";
	}

	private void answer(HttpExchange exchange) throws IOException {
		String query = exchange.getRequestURI().getRawQuery();
		String path = exchange.getRequestURI().getPath() + (query == null ? "" : "?" + query);
		Response response = routes.getOrDefault(path, new Response(404, "{}", Duration.ZERO));
		try {
			Thread.sleep(response.delay());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(response.status(), body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

	private static void assertCode(Runnable call, ErrorCode code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.code()).isEqualTo(code));
	}

	private record Response(int status, String body, Duration delay) {
	}

}
