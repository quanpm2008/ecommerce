package com.example.simulator;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

public record SimulatorConfig(
        int port,
        URI publicOrigin,
        URI frontendReturnBaseUrl,
        String credentialHeader,
        String credential,
        URI webhookUrl,
        String webhookSecret,
        Path stateFile
) {
    public static SimulatorConfig fromEnv() {
        return fromMap(System.getenv());
    }

    public static SimulatorConfig fromMap(Map<String, String> env) {
        int port = parseInt(env.get("SIMULATOR_PORT"), 8089);
        URI publicOrigin = URI.create(nonBlank(env.get("SIMULATOR_PUBLIC_ORIGIN"), "http://localhost:" + port));
        URI frontendReturnBaseUrl = URI.create(nonBlank(
                env.get("SIMULATOR_FRONTEND_RETURN_URL"),
                "http://localhost:5173/checkout"
        ));
        String credentialHeader = nonBlank(
                env.get("SIMULATOR_CREDENTIAL_HEADER"),
                nonBlank(env.get("PAYMENT_PROVIDER_CREDENTIAL_HEADER"), "X-Provider-Key")
        );
        String credential = nonBlank(
                env.get("SIMULATOR_CREDENTIAL"),
                nonBlank(env.get("PAYMENT_PROVIDER_CREDENTIAL"), "demo-provider-dev-key-0123456789")
        );
        URI webhookUrl = URI.create(nonBlank(
                env.get("SIMULATOR_WEBHOOK_URL"),
                "http://127.0.0.1:8086/api/v1/payments/webhooks/demo-provider"
        ));
        String webhookSecret = nonBlank(
                env.get("SIMULATOR_WEBHOOK_SECRET"),
                nonBlank(env.get("PAYMENT_PROVIDER_WEBHOOK_SECRET"), "demo-provider-webhook-secret-0123456789")
        );
        String statePath = nonBlank(env.get("SIMULATOR_STATE_FILE"), "/tmp/payment-simulator-state.json");
        return new SimulatorConfig(
                port,
                publicOrigin,
                frontendReturnBaseUrl,
                credentialHeader,
                credential,
                webhookUrl,
                webhookSecret,
                Path.of(statePath)
        );
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value.trim());
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
