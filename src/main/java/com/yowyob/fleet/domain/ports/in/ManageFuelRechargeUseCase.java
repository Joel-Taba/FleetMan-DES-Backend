package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.FuelRecharge;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Port d'entrée : cas d'utilisation pour la gestion des Recharges de Carburant.
 * Invoqué par l'adaptateur REST (FuelRechargeController).
 */
public interface ManageFuelRechargeUseCase {

    // ── Commandes ─────────────────────────────────────────────────────────────

    record CreateFuelRechargeCommand(
            BigDecimal quantity,
            BigDecimal price,
            Double longitude,
            Double latitude,
            FuelRecharge.StationName stationName,   // optionnel
            UUID vehicleId,
            UUID driverId                           // optionnel
    ) {}

    record UpdateFuelRechargeCommand(
            UUID rechargeId,
            BigDecimal quantity,
            BigDecimal price,
            Double longitude,
            Double latitude,
            FuelRecharge.StationName stationName,
            UUID driverId
    ) {}

    // ── Use Cases ─────────────────────────────────────────────────────────────

    /**
     * Enregistre une nouvelle recharge de carburant.
     * Vérifie l'existence du véhicule et du chauffeur (si fourni).
     * Met à jour le niveau de carburant dans les paramètres opérationnels du véhicule.
     */
    Mono<FuelRecharge> createFuelRecharge(CreateFuelRechargeCommand command);

    /**
     * Récupère une recharge par son identifiant.
     */
    Mono<FuelRecharge> getById(UUID id);

    /**
     * Liste toutes les recharges des véhicules gérés par un manager.
     */
    Flux<FuelRecharge> getAllByManager(UUID managerId);

    /**
     * Liste toutes les recharges d'un véhicule spécifique.
     */
    Flux<FuelRecharge> getByVehicleId(UUID vehicleId);

    /**
     * Liste toutes les recharges effectuées par un chauffeur.
     */
    Flux<FuelRecharge> getByDriverId(UUID driverId);

    /**
     * Liste les recharges dans une plage de dates.
     */
    Flux<FuelRecharge> getByDateRange(LocalDateTime start, LocalDateTime end, UUID managerId);

    /**
     * Calcule la consommation totale de carburant (en litres) pour un véhicule.
     * Utile pour les KPIs de coût opérationnel.
     */
    Mono<BigDecimal> getTotalQuantityByVehicleId(UUID vehicleId);

    /**
     * Calcule le coût total des recharges pour un véhicule.
     */
    Mono<BigDecimal> getTotalCostByVehicleId(UUID vehicleId);

    /**
     * Met à jour une recharge existante.
     */
    Mono<FuelRecharge> update(UpdateFuelRechargeCommand command);

    /**
     * Supprime une recharge.
     */
    Mono<Void> delete(UUID id);
}
