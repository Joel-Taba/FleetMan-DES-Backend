package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.domain.ports.out.SendNotificationPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.FakeNotificationAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.NotificationApiClient;
import com.yowyob.fleet.infrastructure.adapters.outbound.messaging.HttpNotificationAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class NotificationConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "application.notification.mode", havingValue = "fake")
    public SendNotificationPort fakeNotificationPort() {
        return new FakeNotificationAdapter();
    }

    @Bean
    @ConditionalOnProperty(
            name = "application.notification.mode",
            havingValue = "http",
            matchIfMissing = false
    )
    public SendNotificationPort httpNotificationPort(NotificationApiClient client) {
        return new HttpNotificationAdapter(client);
    }
}
