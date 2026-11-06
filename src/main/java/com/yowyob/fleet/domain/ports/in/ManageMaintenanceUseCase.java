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
            UUID driverId,      // optionnel
            Maintenance.Status status,      // null : intervention terminée (saisie après coup)
            String maintenanceType,         // PREVENTIVE / CORRECTIVE / EMERGENCY
            boolean urgent,
            LocalDateTime plannedAt,        // obligatoire si PLANNED
            UUID actorId                    // auteur (chauffeur demandeur ou gestionnaire)
    ) {
        /** Ordre de réparation ouvert depuis un dossier d'incident : planifié, à dater par le gestionnaire. */
        public CreateMaintenanceCommand(String subject, BigDecimal cost, String report, Double longitude, Double latitude,
                                        String locationName, UUID vehicleId, UUID driverId) {
            this(subject, cost, report, longitude, latitude, locationName, vehicleId, driverId,
                    Maintenance.Status.REQUESTED, "CORRECTIVE", false, null, null);
        }
    }

    /** Avancement décidé par le gestionnaire : PLAN, START, COMPLETE, CANCEL. */
    record TransitionCommand(
            UUID maintenanceId,
            UUID actorId,
            String action,
            LocalDateTime plannedAt,
            String garage,
            BigDecimal cost,
            String report,
            String reason
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

    /** Planifier, démarrer, terminer ou annuler (effets sur le véhicule, notification du chauffeur). */
    Mono<Maintenance> transition(TransitionCommand command);

    /** Maintenances qui concernent le chauffeur : ses demandes, celles où il est cité, celles de son véhicule. */
    Flux<Maintenance> getForDriver(UUID driverId);

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
