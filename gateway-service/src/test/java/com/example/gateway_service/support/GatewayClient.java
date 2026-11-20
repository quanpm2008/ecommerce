package com.example.gateway_service.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Real HTTP calls to the running gateway (not MockMvc), so the servlet container's error dispatch, path handling and
 * the proxy's own HTTP client all take part.
 */
public final class GatewayClient {

	private final HttpClient http = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	private final int port;

	private final String[] defaultHeaders;

	public GatewayClient(int port) {
		this(port, new String[0]);
	}

	private GatewayClient(int port, String[] defaultHeaders) {
		this.port = port;
		this.defaultHeaders = defaultHeaders;
	}

	/** A client that sends {@code Authorization: Bearer <token>} with every request. */
	public GatewayClient withToken(String token) {
		return new GatewayClient(port, new String[] { "Authorization", "Bearer " + token });
	}

	public HttpResponse<String> get(String target, String... headers) {
		return send("GET", target, null, headers);
	}

	public HttpResponse<String> send(String method, String target, String body, String... headers) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + target))
				.timeout(Duration.ofSeconds(10))
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofString(body));
		if (defaultHeaders.length > 0) {
			request.headers(defaultHeaders);
		}
		if (headers.length > 0) {
			request.headers(headers);
		}
		try {
			return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

}
