package com.example.order_service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

import static java.nio.file.StandardOpenOption.APPEND;

final class OrderRealServiceE1Support implements AutoCloseable {
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private final Path evidence;
	private final Function<String, String> environment;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

	OrderRealServiceE1Support(String selector) {
		this(selector, System::getenv, Path.of("target", "e1-evidence"));
	}

	OrderRealServiceE1Support(String selector, Function<String, String> environment, Path directory) {
		this.environment = environment;
		if (!selector.matches("[a-z0-9-]+")) {
			throw new IllegalArgumentException("Invalid E1 evidence selector");
		}
		try {
			Files.createDirectories(directory);
			evidence = directory.resolve(selector + ".txt");
			Files.writeString(evidence, "E1 " + selector + System.lineSeparator());
		} catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}

	static String required(String name) {
		return require(name, System.getenv(name));
	}

	void requireInputs(String... names) {
		for (String name : names) {
			input(name);
		}
	}

	void note(String evidenceNonSecret) {
		try {
			Files.writeString(evidence, evidenceNonSecret + System.lineSeparator(), APPEND);
		} catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}

	void revision(String name) {
		String revision = input(name);
		if (!revision.matches("[A-Za-z0-9._-]{1,100}")) {
			note("INVALID_REVISION " + name);
			throw new IllegalStateException("Invalid revision label " + name);
		}
		note(name + " " + revision);
	}

	void scope(String label, String peerKind) {
		switch (peerKind) {
			case "real" -> note("boundary " + label + " peer=real");
			case "mock" -> note("boundary-only " + label + " peer=contract-mock");
			default -> throw new IllegalStateException("Peer kind must be real or mock");
		}
	}

	JsonNode request(String baseEnv, String method, String path, String bearer, String serviceToken, Object body,
			int expected) throws Exception {
		return request(baseEnv, method, path, bearer, serviceToken, body, expected, Map.of());
	}

	JsonNode request(String baseEnv, String method, String path, String bearer, String serviceToken, Object body,
			int expected, Map<String, String> extraHeaders) throws Exception {
		HttpRequest.Builder request;
		try {
			URI base = URI.create(input(baseEnv));
			if (base.getHost() == null || base.getUserInfo() != null || base.getRawQuery() != null
					|| base.getRawFragment() != null || !path.startsWith("/") || path.startsWith("//")
					|| !("http".equals(base.getScheme()) || "https".equals(base.getScheme()))) {
				throw new IllegalArgumentException();
			}
			request = HttpRequest.newBuilder(URI.create(base.toString().replaceFirst("/+$", "") + path))
					.timeout(Duration.ofSeconds(12));
			if (bearer != null) {
				request.header("Authorization", "Bearer " + bearer);
			}
			if (serviceToken != null) {
				request.header("X-Service-Token", serviceToken);
			}
			extraHeaders.forEach(request::header);
			request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
					: HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
			if (body != null) {
				request.header("Content-Type", "application/json");
			}
		} catch (IllegalArgumentException | JacksonException invalid) {
			note(baseEnv + " INVALID_REQUEST");
			throw new IllegalStateException("Invalid request settings for " + baseEnv);
		}
		String route = path.split("\\?", 2)[0].replaceAll(
				"(?i)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}", "{id}");
		HttpResponse<String> response;
		try {
			response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		} catch (IOException failure) {
			note(method + " " + baseEnv + route + " TRANSPORT " + failure.getClass().getSimpleName());
			throw new AssertionError("E1 transport failure for " + baseEnv + " " + route);
		}
		note(method + " " + baseEnv + route + " status=" + response.statusCode() + " expected=" + expected);
		if (response.statusCode() != expected) {
			throw new AssertionError("E1 HTTP status for " + baseEnv + " " + route + ": " + response.statusCode());
		}
		try {
			return response.body().isBlank() ? JSON.nullNode() : JSON.readTree(response.body());
		} catch (JacksonException invalid) {
			note(baseEnv + " INVALID_JSON");
			throw new AssertionError("Invalid JSON for " + baseEnv + " " + route);
		}
	}

	private String input(String name) {
		String value = environment.apply(name);
		if (value == null || value.isBlank()) {
			note("MISSING_INPUT " + name);
		}
		return require(name, value);
	}

	private static String require(String name, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Missing E1 input " + name);
		}
		return value;
	}

	@Override
	public void close() {
		http.close();
	}
}
