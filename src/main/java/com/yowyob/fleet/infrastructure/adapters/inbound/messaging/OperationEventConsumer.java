package com.yowyob.fleet.infrastructure.adapters.inbound.messaging;

import com.yowyob.fleet.domain.model.Notification;
import com.yowyob.fleet.domain.ports.out.NotificationHistoryRepositoryPort;
import com.yowyob.fleet.domain.ports.out.OperationEventPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.NotificationType;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SendNotificationRequest;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.NotificationApiClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer Kafka pour les événements du module Opérations Terrain.
 * Écoute le topic `operation-events-topic` et :
 *   1. Persiste une notification in-app dans l'historique du Fleet Manager
 *   2. Envoie une notification push/email via le service de notification externe
 *
 * Actif uniquement si Kafka est configuré (désactivable via propriété).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers", matchIfMissing = false)
public class OperationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OperationEventConsumer.class);

    private final NotificationHistoryRepositoryPort notificationHistory;
    private final NotificationApiClient notificationApiClient;

    // IDs des templates de notification (définis dans application.yml)
    private static final int TEMPLATE_MAINTENANCE = 8;  // Template : Nouvelle maintenance déclarée
    private static final int TEMPLATE_INCIDENT     = 9;  // Template : Incident signalé
    private static final int TEMPLATE_INCIDENT_CRITICAL = 10; // Template : Incident CRITIQUE

    // ── Maintenance Created ───────────────────────────────────────────────────

    @KafkaListener(
        topics = "${application.kafka.topics.operation-events:operation-events-topic}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOperationEvent(Object rawEvent) {
        // Le dispatcher détermine le type d'événement et route vers le bon handler
        if (rawEvent instanceof OperationEventPort.MaintenanceCreatedEvent event) {
            handleMaintenanceCreated(event).subscribe();
        } else if (rawEvent instanceof OperationEventPort.IncidentReportedEvent event) {
            handleIncidentReported(event).subscribe();
        } else {
            log.debug("Événement opération non reconnu : {}", rawEvent.getClass().getSimpleName());
        }
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    private Mono<Void> handleMaintenanceCreated(OperationEventPort.MaintenanceCreatedEvent event) {
        log.info("📥 [OperationConsumer] MaintenanceCreated — vehicleId: {}, managerId: {}",
                event.vehicleId(), event.fleetManagerId());

        if (event.fleetManagerId() == null) {
            log.warn("⚠️ fleetManagerId absent dans MaintenanceCreatedEvent — notification ignorée");
            return Mono.empty();
        }

        String title   = "Nouvelle maintenance déclarée";
        String message = String.format("Une maintenance a été enregistrée pour le véhicule %s : \"%s\"",
                event.vehicleRegistration(), event.subject());

        // 1. Notification in-app
        Mono<Void> inApp = saveInAppNotification(
                event.fleetManagerId(), title, message, "MAINTENANCE",
                Map.of("maintenanceId", event.maintenanceId().toString(),
                       "vehicleId",     event.vehicleId().toString())
        );

        // 2. Notification push/email (fire & forget)
        sendPushNotification(
                event.fleetManagerId(), TEMPLATE_MAINTENANCE,
                Map.of("subject",      event.subject(),
                       "vehicleReg",   event.vehicleRegistration() != null ? event.vehicleRegistration() : "")
        ).subscribe();

        return inApp;
    }

    private Mono<Void> handleIncidentReported(OperationEventPort.IncidentReportedEvent event) {
        log.info("📥 [OperationConsumer] IncidentReported — type: {}, severity: {}, critical: {}",
                event.incidentType(), event.severity(), event.isCritical());

        if (event.fleetManagerId() == null) {
            log.warn("⚠️ fleetManagerId absent dans IncidentReportedEvent — notification ignorée");
            return Mono.empty();
        }

        String title   = event.isCritical()
                ? "🚨 Incident CRITIQUE signalé"
                : "Incident signalé sur un véhicule";
        String message = String.format("Incident %s (%s) sur le véhicule %s.",
                event.incidentType(), event.severity(), event.vehicleRegistration());

        int templateId = event.isCritical() ? TEMPLATE_INCIDENT_CRITICAL : TEMPLATE_INCIDENT;

        // 1. Notification in-app
        Mono<Void> inApp = saveInAppNotification(
                event.fleetManagerId(), title, message, "INCIDENT",
                Map.of("incidentId",  event.incidentId().toString(),
                       "vehicleId",   event.vehicleId().toString(),
                       "isCritical",  String.valueOf(event.isCritical()))
        );

        // 2. Notification push/email (fire & forget)
        sendPushNotification(
                event.fleetManagerId(), templateId,
                Map.of("incidentType", event.incidentType(),
                       "severity",     event.severity(),
                       "vehicleReg",   event.vehicleRegistration() != null ? event.vehicleRegistration() : "")
        ).subscribe();

        return inApp;
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    /**
     * Persiste une notification dans l'historique in-app du Fleet Manager.
     */
    private Mono<Void> saveInAppNotification(UUID userId, String title, String message,
                                              String type, Map<String, Object> data) {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .dataJson(data.toString())
                .build();
        return notificationHistory.save(notification)
                .doOnError(e -> log.error("❌ Échec sauvegarde notification in-app pour {}: {}",
                        userId, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * Envoie une notification push/email via le service externe.
     * Dégradé gracieux : un échec ne propage pas d'erreur.
     */
    private Mono<Void> sendPushNotification(UUID managerId, int templateId, Map<String, Object> data) {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .notificationType(NotificationType.PUSH)
                .templateId(templateId)
                .to(List.of(managerId.toString()))
                .data(data)
                .build();

        return notificationApiClient.sendNotification(request)
                .doOnSuccess(r -> log.debug("✅ Notification push envoyée au manager {}", managerId))
                .doOnError(e -> log.warn("⚠️ Échec notification push pour {}: {}", managerId, e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}
