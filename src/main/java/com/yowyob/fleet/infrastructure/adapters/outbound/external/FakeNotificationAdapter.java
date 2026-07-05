package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.ports.out.SendNotificationPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SendNotificationRequest;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Simulateur du service de notification externe.
 * Activé par : application.notification.mode=fake
 * Logge le message au lieu de l'envoyer réellement.
 */
@Slf4j
public class FakeNotificationAdapter implements SendNotificationPort {

    @Override
    public Mono<Boolean> sendNotification(SendNotificationRequest request) {
        log.info("🛠 [FAKE NOTIFICATION] template={} to={} data={}",
                request.templateId(), request.to(), request.data());
        return Mono.just(true);
    }
}
