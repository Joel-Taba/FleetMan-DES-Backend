package com.yowyob.fleet.domain.ports.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port sortant événementiel : permet au domaine de publier des événements
 * métier sans dépendre de Spring ApplicationEventPublisher ni de Kafka.
 *
 * L'adaptateur infrastructure (KafkaOperationEventAdapter) implémente ce port.
 * Un adaptateur de substitution (LogOperationEventAdapter) est prévu pour
 * les environnements sans Kafka.
 *
 * Les events sont des records imbriqués — 100% Java, aucune dépendance Spring.
 */
public interface OperationEventPort {

    // ── Events ────────────────────────────────────────────────────────────────

    /**
     * Événement publié après la création d'une maintenance.
     * Le module Notification l'écoute pour alerter le Fleet Manager.
     *
     * @param fleetManagerId ID du manager à notifier (résolu via le véhicule)
     */
    record MaintenanceCreatedEvent(
            UUID maintenanceId,
            String subject,
            UUID vehicleId,
            String vehicleRegistration,
            UUID driverId,              // peut être null
            UUID fleetManagerId
    ) {}

    /**
     * Événement publié après la déclaration d'un incident.
     * Si isCritical est true, la notification est envoyée en priorité haute.
     *
     * @param isCritical true si la sévérité est HIGH ou CRITICAL
     */
    record IncidentReportedEvent(
            UUID incidentId,
            String incidentType,
            String severity,
            UUID vehicleId,
            String vehicleRegistration,
            UUID driverId,              // peut être null
            UUID fleetManagerId,
            boolean isCritical
    ) {}

    // ── Méthodes ──────────────────────────────────────────────────────────────

    /**
     * Publie un événement de création de maintenance.
     * Déclenche une notification au Fleet Manager concerné.
     */
    Mono<Void> publishMaintenanceCreated(MaintenanceCreatedEvent event);

    /**
     * Publie un événement de déclaration d'incident.
     * Déclenche une notification prioritaire si isCritical est true.
     */
    Mono<Void> publishIncidentReported(IncidentReportedEvent event);
}
