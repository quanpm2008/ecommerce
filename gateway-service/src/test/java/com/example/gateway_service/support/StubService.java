package com.example.gateway_service.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A fake downstream service on a random local port: records every request it gets and answers with {@link #reply}.
 * Built on the JDK's HTTP server so the tests need no extra dependency.
 */
public final class StubService implements AutoCloseable {

	/** What the stub saw. {@code target} is the raw path plus query, exactly as received. */
	public record Received(String method, String target, Map<String, List<String>> headers, String body) {

		public String header(String name) {
			return headers.entrySet().stream()
					.filter(entry -> entry.getKey().equalsIgnoreCase(name))
					.map(entry -> String.join(",", entry.getValue()))
					.findFirst()
					.orElse(null);
		}

	}

	/** The stub's answer. A {@code null} body sends none (as for 204). */
	public record Reply(int status, String contentType, String body, Map<String, String> headers, Duration delay) {

		public static Reply json(int status, String body) {
			return new Reply(status, "application/json", body, Map.of(), Duration.ZERO);
		}

		public Reply withHeader(String name, String value) {
			Map<String, String> all = new java.util.LinkedHashMap<>(headers);
			all.put(name, value);
			return new Reply(status, contentType, body, all, delay);
		}

		public Reply after(Duration wait) {
			return new Reply(status, contentType, body, headers, wait);
		}

	}

	private final String name;

	private final HttpServer server;

	private final List<Received> received = new CopyOnWriteArrayList<>();

	private volatile Reply reply;

	private StubService(String name) throws IOException {
		this.name = name;
		this.reply = Reply.json(200, "{\"data\":{\"service\":\"" + name + "\"}}");
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.setExecutor(Executors.newCachedThreadPool());
		this.server.createContext("/", this::handle);
		this.server.start();
	}

	public static StubService start(String name) {
		try {
			return new StubService(name);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** A local URI where nothing listens, so connecting is refused. */
	public static String refusedUri() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return "http://127.0.0.1:" + socket.getLocalPort();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public String name() {
		return name;
	}

	public String uri() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	public void reply(Reply next) {
		this.reply = next;
	}

	public List<Received> received() {
		return List.copyOf(received);
	}

	public Received last() {
		return received.getLast();
	}

	public void reset() {
		received.clear();
		reply = Reply.json(200, "{\"data\":{\"service\":\"" + name + "\"}}");
	}

	@Override
	public void close() {
		server.stop(0);
	}

	private void handle(HttpExchange exchange) throws IOException {
		try (exchange; InputStream in = exchange.getRequestBody()) {
			String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			String target = exchange.getRequestURI().getRawPath()
					+ (exchange.getRequestURI().getRawQuery() != null ? "?" + exchange.getRequestURI().getRawQuery() : "");
			received.add(new Received(exchange.getRequestMethod(), target, Map.copyOf(exchange.getRequestHeaders()), body));
			Reply answer = reply;
			if (!answer.delay().isZero()) {
				Thread.sleep(answer.delay());
			}
			answer.headers().forEach(exchange.getResponseHeaders()::set);
			if (answer.body() == null) {
				exchange.sendResponseHeaders(answer.status(), -1);
				return;
			}
			byte[] bytes = answer.body().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", answer.contentType());
			exchange.sendResponseHeaders(answer.status(), bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
