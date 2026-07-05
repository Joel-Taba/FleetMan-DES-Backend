package com.yowyob.fleet.infrastructure.adapters.outbound.notification;

import com.yowyob.fleet.domain.model.AlertEvent;
import com.yowyob.fleet.domain.ports.out.AlertEventPersistencePort;
import com.yowyob.fleet.domain.ports.out.SendAlertPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Adaptateur d'envoi d'alertes in-app.
 *
 * Implémente {@link SendAlertPort} pour le canal in-app :
 * persiste l'événement dans la table {@code fleet.alert_events}.
 *
 * Ce canal est TOUJOURS actif pour tous les événements d'alerte.
 * Le frontend interroge {@code GET /api/v1/alerts/events} pour récupérer les notifications.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InAppAlertAdapter implements SendAlertPort {

    private final AlertEventPersistencePort eventPersistence;

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
        // Email non implémenté dans cette phase — prévu pour le branchement Kernel file-core / SMTP
        // TODO : intégrer JavaMailSender ou API SMTP externe
        log.info("📧 [TODO] Email à envoyer pour alerte '{}' → manager {}",
                event.getTitle(), event.getManagerId());
        return Mono.empty();
    }
}
