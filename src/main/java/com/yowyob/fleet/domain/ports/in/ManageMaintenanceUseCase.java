package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.Maintenance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Port d'entrée : cas d'utilisation pour la gestion des Maintenances.
 * Invoqué par l'adaptateur REST (MaintenanceController).
 *
 * Les commandes sont des records imbriqués pour éviter les méthodes
 * à longue liste de paramètres.
 */
public interface ManageMaintenanceUseCase {

    // ── Commandes ─────────────────────────────────────────────────────────────

    record CreateMaintenanceCommand(
            String subject,
            BigDecimal cost,
            String report,
            Double longitude,
            Double latitude,
            String locationName,
            UUID vehicleId,
            UUID driverId       // optionnel
    ) {}

    record UpdateMaintenanceCommand(
            UUID maintenanceId,
            String subject,
            BigDecimal cost,
            String report,
            Double longitude,
            Double latitude,
            String locationName
    ) {}

    // ── Use Cases ─────────────────────────────────────────────────────────────

    /**
     * Crée une nouvelle maintenance pour un véhicule.
     * Vérifie l'existence du véhicule et du chauffeur (si fourni).
     * Publie un événement de notification au Fleet Manager.
     */
    Mono<Maintenance> createMaintenance(CreateMaintenanceCommand command);

    /**
     * Récupère une maintenance par son identifiant.
     */
    Mono<Maintenance> getById(UUID id);

    /**
     * Liste toutes les maintenances des véhicules gérés par un manager.
     */
    Flux<Maintenance> getAllByManager(UUID managerId);

    /**
     * Liste toutes les maintenances d'un véhicule spécifique.
     */
    Flux<Maintenance> getByVehicleId(UUID vehicleId);

    /**
     * Liste toutes les maintenances impliquant un chauffeur spécifique.
     */
    Flux<Maintenance> getByDriverId(UUID driverId);

    /**
     * Liste les maintenances dans une plage de dates.
     */
    Flux<Maintenance> getByDateRange(LocalDateTime start, LocalDateTime end, UUID managerId);

    /**
     * Compte le nombre de maintenances impliquant un chauffeur.
     * Utile pour les KPIs du tableau de bord.
     */
    Mono<Long> countByDriverId(UUID driverId);

    /**
     * Met à jour une maintenance existante (rapport, coût, localisation).
     */
    Mono<Maintenance> update(UpdateMaintenanceCommand command);

    /**
     * Supprime une maintenance.
     */
    Mono<Void> delete(UUID id);
}
