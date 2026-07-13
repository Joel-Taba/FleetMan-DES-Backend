package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TripR2dbcRepository
        extends ReactiveCrudRepository<TripEntity, UUID> {
    Mono<TripEntity> findByDriverIdAndStatus(UUID driverId, String status);

    Mono<TripEntity> findByVehicleIdAndStatus(UUID vehicleId, String status);

    Mono<TripEntity> findByTripCode(String tripCode);

    Flux<TripEntity> findAllByCreatedBy(UUID createdBy);

    Flux<TripEntity> findAllByCreatedByAndFleetId(UUID createdBy, UUID fleetId);

    Flux<TripEntity> findAllByDriverId(UUID driverId);

    @Query("SELECT COALESCE(SUM(t.distance_km), 0) FROM fleet.trips t " +
            "JOIN fleet.vehicles v ON t.vehicle_id = v.id " +
            "WHERE v.fleet_id = :fleetId")
    Mono<Double> getTotalDistanceByFleetId(UUID fleetId);

    // G9 FIX: Permet à l'Admin de voir TOUS les trajets de son organisation
    @Query("SELECT t.* FROM fleet.trips t " +
            "JOIN fleet.fleets f ON t.fleet_id = f.id " +
            "JOIN fleet.fleet_managers fm ON f.manager_id = fm.user_id " +
            "WHERE fm.company_name = (SELECT company_name FROM fleet.fleet_managers WHERE user_id = :userId) " +
            "ORDER BY t.start_date DESC, t.start_time DESC")
    Flux<TripEntity> findAllBySameCompanyAsUser(UUID userId);
}
