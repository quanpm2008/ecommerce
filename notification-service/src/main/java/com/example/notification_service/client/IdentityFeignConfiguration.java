package com.example.notification_service.client;

import feign.Logger;
import feign.Retryer;
import org.springframework.context.annotation.Bean;

/** Client-local configuration: authorization headers and source payloads must never enter Feign logs. */
public class IdentityFeignConfiguration {
    @Bean
    Logger.Level identityLogLevel() {
        return Logger.Level.NONE;
    }

    @Bean
    Retryer identityRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
