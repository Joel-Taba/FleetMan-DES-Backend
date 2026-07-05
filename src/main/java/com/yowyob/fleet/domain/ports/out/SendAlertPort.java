package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.AlertEvent;
import reactor.core.publisher.Mono;

/**
 * Port sortant : envoi des alertes vers les canaux de communication.
 *
 * Implémenté par :
 * - InAppAlertAdapter (notifications in-app via table alert_events — toujours actif)
 * - EmailAlertAdapter (email SMTP — conditionnel selon la règle)
 *
 * L'architecture est prête pour ajouter des canaux supplémentaires
 * (SMS, Push mobile, Slack...) sans modifier le domaine ni le service.
 */
public interface SendAlertPort {

    /**
     * Envoie une alerte in-app (persist dans alert_events).
     * Toujours appelé pour tout événement d'alerte.
     */
    Mono<AlertEvent> sendInApp(AlertEvent event);

    /**
     * Envoie un email de notification.
     * Appelé uniquement si la règle a actionType = EMAIL.
     * Fire & forget — les erreurs d'envoi email ne bloquent pas le flux principal.
     */
    Mono<Void> sendEmail(AlertEvent event, String recipientEmail);
}
