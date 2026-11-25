package com.example.payment_service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ProviderMockFixtureHostTests {
    @Test
    void hostsProviderFixtureForLocalBrokerRunner() throws Exception {
        String callback = System.getProperty("provider.fixture.callback");
        boolean host = Boolean.getBoolean("provider.fixture.host");
        try (var fixture = new ProviderMockFixture(Clock.systemUTC(), callback == null ? null : URI.create(callback));
             var client = HttpClient.newHttpClient()) {
            URI readiness = fixture.origin().resolve("/__fixture/ready");
            assertThat(client.send(HttpRequest.newBuilder(readiness).GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
            var ready = client.send(HttpRequest.newBuilder(readiness).timeout(Duration.ofSeconds(2))
                    .header(ProviderMockFixture.CONTROL_HEADER, ProviderMockFixture.CONTROL_KEY).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(ready.statusCode()).isEqualTo(200);
            assertThat(new JsonMapper().readTree(ready.body()).path("ready").asBoolean()).isTrue();
            if (host) {
                System.out.println("PAYMENT_PROVIDER_FIXTURE_READY=" + fixture.origin());
                System.out.flush();
                fixture.waitForRunner(Duration.ofSeconds(Long.getLong("provider.fixture.hostTimeoutSeconds", 300L)));
            }
        }
    }
}
