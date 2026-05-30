package com.yowyob.fleet.infrastructure.adapters.outbound.messaging;

import com.yowyob.fleet.domain.ports.out.SendNotificationPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SendNotificationRequest;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.NotificationApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.notification.enabled", havingValue = "true")
public class HttpNotificationAdapter implements SendNotificationPort {

    private final NotificationApiClient notificationApiClient;

    @Override
    public Mono<Boolean> sendNotification(SendNotificationRequest request) {
        log.info("📧 Sending HTTP Notification [Template: {}] to {}", request.templateId(), request.to());
        
        return notificationApiClient.sendNotification(request)
                .thenReturn(true)
                .onErrorResume(e -> {
                    log.error("Failed to send notification via HTTP: {}", e.getMessage());
                    return Mono.just(false);
                });
    }
}