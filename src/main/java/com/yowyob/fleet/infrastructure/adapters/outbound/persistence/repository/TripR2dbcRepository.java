package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity; // Assure-toi que cette entité existe ou est créée
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Repository
public interface TripR2dbcRepository extends ReactiveCrudRepository<TripEntity, UUID> {
    
    // Trouver le trajet actif d'un chauffeur
    Mono<TripEntity> findByDriverIdAndStatus(UUID driverId, String status);
    
    // Trouver le trajet actif d'un véhicule (pour vérifier dispo)
    Mono<TripEntity> findByVehicleIdAndStatus(UUID vehicleId, String status);

    // --- AJOUT TÂCHE 6.2 (JOINTURE) ---
    // Somme des distances de tous les trajets effectués par les véhicules d'une flotte donnée
    // CORRECTION : Ajout de COALESCE pour retourner 0.0 au lieu de NULL si aucun trajet n'existe
    @Query("SELECT COALESCE(SUM(t.distance_km), 0) FROM fleet.trips t " +
            "JOIN fleet.vehicles v ON t.vehicle_id = v.id " +
            "WHERE v.fleet_id = :fleetId")
    Mono<Double> getTotalDistanceByFleetId(UUID fleetId);
}