package com.example.notification_service;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "notification.retention.enabled=false",
        "notification.internal.service-token=notification-outgoing-test-0123456789abcdef",
        "notification.internal.clients.order-service=order-service-test-0123456789abcdef",
        "notification.internal.clients.payment-service=payment-service-test-0123456789abcdef",
        "notification.internal.clients.identity-service=identity-service-test-0123456789abcdef",
        "notification.internal.clients.product-service=product-service-test-0123456789abcdef",
        "notification.internal.clients.finance-service=finance-service-test-0123456789abcdef"})
@AutoConfigureMockMvc
@Import(IntegrationTestSupport.Infrastructure.class)
public abstract class IntegrationTestSupport {
    public static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    protected static final Map<UUID, String> RECIPIENTS = new ConcurrentHashMap<>();
    protected static final Map<String, String> SESSIONS = new ConcurrentHashMap<>();
    private static final HttpServer IDENTITY = identityServer();

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("notification.clients.identity-url", () -> "http://localhost:" + IDENTITY.getAddress().getPort());
    }

    @BeforeEach
    void resetState() {
        jdbc.update("DELETE FROM notification_inbox");
        jdbc.update("DELETE FROM notifications");
        RECIPIENTS.clear();
        SESSIONS.clear();
    }

    protected static void session(String token, UUID user, String accountType, String accountStatus,
            String userStatus, boolean active, boolean permitted) {
        RECIPIENTS.put(user, accountType);
        SESSIONS.put("Bearer " + token, "{\"data\":{\"userId\":\"" + user + "\",\"accountType\":\""
                + accountType + "\",\"accountStatus\":\"" + accountStatus + "\",\"userStatus\":\"" + userStatus
                + "\",\"accessMode\":\"FULL\",\"authzVersion\":1,\"roles\":[],\"permissions\":"
                + (permitted ? "[\"notification:read:own\"]" : "[]") + ",\"sessionActive\":" + active + "}}");
    }

    private static HttpServer identityServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/v1/identity/", exchange -> {
                int status = 200;
                String body;
                if (!"notification-outgoing-test-0123456789abcdef".equals(exchange.getRequestHeaders().getFirst("X-Service-Token"))) {
                    status = 401;
                    body = "{\"error\":{\"code\":\"UNAUTHENTICATED\"}}";
                } else if (exchange.getRequestURI().getPath().endsWith("/authorization-context")) {
                    body = SESSIONS.get(exchange.getRequestHeaders().getFirst("Authorization"));
                    if (body == null) { status = 401; body = "{\"error\":{\"code\":\"TOKEN_EXPIRED\"}}"; }
                } else {
                    String path = exchange.getRequestURI().getPath();
                    UUID id = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
                    String type = RECIPIENTS.get(id);
                    if (type == null) { status = 404; body = "{\"error\":{\"code\":\"NOT_FOUND\"}}"; }
                    else { body = "{\"data\":{\"userId\":\"" + id + "\",\"accountType\":\"" + type + "\"}}"; }
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
                exchange.close();
            });
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
            return server;
        } catch (IOException ex) { throw new ExceptionInInitializerError(ex); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Infrastructure {
        @Bean @ServiceConnection
        PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:16"); }

        @Bean @Primary
        Clock testClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
