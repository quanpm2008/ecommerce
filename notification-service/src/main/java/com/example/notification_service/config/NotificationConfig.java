package com.example.notification_service.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationConfig {
    @Bean
    Clock notificationClock() {
        return Clock.systemUTC();
    }
}
