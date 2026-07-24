package com.yowyob.fleet.domain.ports.out;

import reactor.core.publisher.Mono;

/**
 * Port sortant vers notification-controller Kernel (RT-Comops).
 * Envoi de notifications externes (email pour l'instant — SMS/push/websocket
 * partagent le même contrat côté Kernel, extensible sans changer ce port).
 */
public interface ExternalNotificationPort {

    /**
     * Envoie un email via le service de notification Kernel.
     * Fire & forget côté appelant : un échec ne doit jamais bloquer le flux
     * métier (création d'incident, de maintenance...) qui déclenche l'envoi.
     */
    Mono<Void> sendEmail(String recipientEmail, String subject, String body);
}
