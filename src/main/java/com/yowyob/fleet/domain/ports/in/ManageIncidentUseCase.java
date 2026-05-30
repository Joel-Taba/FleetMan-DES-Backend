package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.Incident;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Port d'entrée : cas d'utilisation pour la gestion des Incidents terrain.
 * Invoqué par l'adaptateur REST (IncidentController).
 */
public interface ManageIncidentUseCase {

    // ── Commandes ─────────────────────────────────────────────────────────────

    record CreateIncidentCommand(
            Incident.Type type,
            String description,
            Incident.Severity severity,     // optionnel — défaut MEDIUM
            BigDecimal cost,                // optionnel — peut être estimé plus tard
            Double longitude,
            Double latitude,
            String witnessName,             // optionnel
            String witnessContact,          // optionnel
            String reportedBy,
            UUID vehicleId,
            UUID driverId                   // optionnel
    ) {}

    record UpdateIncidentCommand(
            UUID incidentId,
            String description,
            Incident.Severity severity,
            BigDecimal cost,
            String report,
            String witnessName,
            String witnessContact,
            String policeReportNumber,
            String insuranceClaimNumber,
            Double longitude,
            Double latitude
    ) {}

    // ── Use Cases ─────────────────────────────────────────────────────────────

    /**
     * Déclare un nouvel incident sur un véhicule.
     * Vérifie l'existence du véhicule et du chauffeur (si fourni).
     * Publie un événement prioritaire si l'incident est CRITICAL ou HIGH.
     */
    Mono<Incident> createIncident(CreateIncidentCommand command);

    /**
     * Récupère un incident par son identifiant.
     */
    Mono<Incident> getById(UUID id);

    /**
     * Liste tous les incidents des véhicules gérés par un manager.
     */
    Flux<Incident> getAllByManager(UUID managerId);

    /**
     * Liste les incidents d'un véhicule spécifique.
     */
    Flux<Incident> getByVehicleId(UUID vehicleId);

    /**
     * Liste les incidents impliquant un chauffeur spécifique.
     */
    Flux<Incident> getByDriverId(UUID driverId);

    /**
     * Liste les incidents par type (ACCIDENT, BREAKDOWN, etc.).
     */
    Flux<Incident> getByType(Incident.Type type, UUID managerId);

    /**
     * Liste les incidents par niveau de gravité.
     */
    Flux<Incident> getBySeverity(Incident.Severity severity, UUID managerId);

    /**
     * Liste les incidents par statut dans le cycle de vie.
     */
    Flux<Incident> getByStatus(Incident.Status status, UUID managerId);

    /**
     * Liste uniquement les incidents encore ouverts (REPORTED ou UNDER_INVESTIGATION).
     * Utile pour le tableau de bord du manager.
     */
    Flux<Incident> getOpenIncidents(UUID managerId);

    /**
     * Liste les incidents dans une plage de dates.
     */
    Flux<Incident> getByDateRange(LocalDateTime start, LocalDateTime end, UUID managerId);

    /**
     * Met à jour les informations d'un incident (description, coût, témoins, numéros officiels).
     */
    Mono<Incident> update(UpdateIncidentCommand command);

    /**
     * Met à jour uniquement le statut d'un incident (transition dans la machine à états).
     */
    Mono<Incident> updateStatus(UUID id, Incident.Status newStatus);

    /**
     * Compte le nombre total d'incidents pour un véhicule.
     * Utile pour les KPIs.
     */
    Mono<Long> countByVehicleId(UUID vehicleId);

    /**
     * Calcule le coût total des incidents pour un véhicule.
     * Utile pour les KPIs financiers.
     */
    Mono<BigDecimal> getTotalCostByVehicleId(UUID vehicleId);

    /**
     * Calcule le coût total des incidents impliquant un chauffeur.
     */
    Mono<BigDecimal> getTotalCostByDriverId(UUID driverId);

    /**
     * Supprime un incident.
     */
    Mono<Void> delete(UUID id);
}
