package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.DriverDocument;
import com.yowyob.fleet.domain.model.VehicleDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port sortant — Contrat de persistance pour les documents légaux.
 */
public interface DocumentPersistencePort {

    // ── Documents Véhicule ────────────────────────────────────────────────────

    Mono<VehicleDocument> saveVehicleDoc(VehicleDocument doc);
    Mono<VehicleDocument> findVehicleDocById(UUID id);
    Flux<VehicleDocument> findVehicleDocsByVehicleId(UUID vehicleId);

    /** Tous les documents véhicule d'un manager (via JOIN sur vehicles). */
    Flux<VehicleDocument> findAllVehicleDocsByManagerId(UUID managerId);

    /** Documents véhicule expirant avant une date donnée. */
    Flux<VehicleDocument> findVehicleDocsExpiringBefore(LocalDate date);

    Mono<Boolean> existsVehicleDocById(UUID id);
    Mono<Void> deleteVehicleDocById(UUID id);

    // ── Documents Conducteur ──────────────────────────────────────────────────

    Mono<DriverDocument> saveDriverDoc(DriverDocument doc);
    Mono<DriverDocument> findDriverDocById(UUID id);
    Flux<DriverDocument> findDriverDocsByDriverId(UUID driverId);

    /** Tous les documents conducteur d'un manager (via JOIN sur drivers). */
    Flux<DriverDocument> findAllDriverDocsByManagerId(UUID managerId);

    /** Documents conducteur expirant avant une date donnée. */
    Flux<DriverDocument> findDriverDocsExpiringBefore(LocalDate date);

    Mono<Boolean> existsDriverDocById(UUID id);
    Mono<Void> deleteDriverDocById(UUID id);
}
