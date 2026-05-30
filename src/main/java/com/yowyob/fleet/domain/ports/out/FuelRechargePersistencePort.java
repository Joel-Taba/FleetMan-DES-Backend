package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.FuelRecharge;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Port sortant : contrat de persistance des Recharges de Carburant.
 * Implémenté par FuelRechargePersistenceAdapter dans la couche infrastructure (R2DBC).
 */
public interface FuelRechargePersistencePort {

    Mono<FuelRecharge> save(FuelRecharge fuelRecharge);

    Mono<FuelRecharge> findById(UUID id);

    Flux<FuelRecharge> findAll();

    /**
     * Récupère toutes les recharges des véhicules appartenant à un manager.
     */
    Flux<FuelRecharge> findAllByManagerId(UUID managerId);

    Flux<FuelRecharge> findByVehicleId(UUID vehicleId);

    Flux<FuelRecharge> findByDriverId(UUID driverId);

    Flux<FuelRecharge> findByDateRange(LocalDateTime start, LocalDateTime end, UUID managerId);

    // ── Agrégats pour KPIs ────────────────────────────────────────────────────

    /**
     * Somme des quantités rechargées (en litres) pour un véhicule.
     * Utile pour le suivi de la consommation.
     */
    Mono<BigDecimal> getTotalQuantityByVehicleId(UUID vehicleId);

    /**
     * Somme des coûts de toutes les recharges pour un véhicule.
     */
    Mono<BigDecimal> getTotalCostByVehicleId(UUID vehicleId);

    Mono<Boolean> existsById(UUID id);

    Mono<Void> deleteById(UUID id);
}
