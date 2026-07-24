package com.yowyob.fleet.infrastructure.adapters.outbound.external.client;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

/**
 * Client notification-controller Kernel.
 * Réponse ({@code NotificationDelivery}) non enveloppée dans {@code ApiResponse}
 * (contrairement aux autres modules Kernel) — confirmé sur la spec OpenAPI et en
 * test réel. On draine juste le statut HTTP, le détail ne nous intéresse pas.
 */
@HttpExchange
public interface KernelNotificationApiClient {

    @PostExchange("/api/notifications/deliveries")
    Mono<Void> sendDelivery(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationId,
            @RequestBody SendDeliveryRequest request);

    record SendDeliveryRequest(
            String recipientAddress,
            String channel,
            String subject,
            String body
    ) {}
}
