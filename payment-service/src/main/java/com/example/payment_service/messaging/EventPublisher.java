package com.example.payment_service.messaging;

import java.util.Map;

public interface EventPublisher {

    boolean isConfigured();

    void publish(String eventType, Map<String, Object> payload);
}
