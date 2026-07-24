package com.yowyob.fleet.infrastructure.adapters.outbound.notification;

import com.yowyob.fleet.domain.model.AlertEvent;
import com.yowyob.fleet.domain.ports.out.AlertEventPersistencePort;
import com.yowyob.fleet.domain.ports.out.ExternalNotificationPort;
import com.yowyob.fleet.domain.ports.out.SendAlertPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Adaptateur d'envoi d'alertes in-app + email (via notification-controller Kernel).
 *
 * Implémente {@link SendAlertPort} :
 * - le canal in-app persiste l'événement dans la table {@code fleet.alert_events},
 *   TOUJOURS actif pour tous les événements d'alerte ;
 * - le canal email délègue à {@link ExternalNotificationPort} (Kernel RT-Comops).
 * Le frontend interroge {@code GET /api/v1/alerts/events} pour récupérer les notifications in-app.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InAppAlertAdapter implements SendAlertPort {

    private final AlertEventPersistencePort eventPersistence;
    private final ExternalNotificationPort externalNotificationPort;

    @Override
    public Mono<AlertEvent> sendInApp(AlertEvent event) {
        return eventPersistence.save(event)
                .doOnSuccess(saved ->
                        log.debug("🔔 Notification in-app créée [{}] pour manager {}",
                                saved.getTitle(), saved.getManagerId()))
                .doOnError(e ->
                        log.error("❌ Erreur persistence notification in-app: {}", e.getMessage()));
    }

    @Override
    public Mono<Void> sendEmail(AlertEvent event, String recipientEmail) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("⚠️ Envoi email ignoré (pas d'adresse) pour alerte '{}' → manager {}",
                    event.getTitle(), event.getManagerId());
            return Mono.empty();
        }
        return externalNotificationPort.sendEmail(recipientEmail, event.getTitle(), event.getMessage())
                .doOnError(e -> log.warn("⚠️ Échec envoi email Kernel pour alerte '{}' : {}",
                        event.getTitle(), e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }
}
