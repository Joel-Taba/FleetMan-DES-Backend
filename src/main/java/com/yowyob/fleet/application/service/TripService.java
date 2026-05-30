package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.TripException;
import com.yowyob.fleet.domain.model.Trip;
import com.yowyob.fleet.domain.ports.in.ManageTripUseCase;
import com.yowyob.fleet.domain.ports.out.*;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.RedisTelemetryAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.OperationalParameterR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService implements ManageTripUseCase {

    private final TripR2dbcRepository tripRepository;
    private final VehicleLocalR2dbcRepository vehicleRepository;
    private final DriverPersistencePort driverPersistence;
    private final OperationalParameterR2dbcRepository operationalRepo;
    private final RedisTelemetryAdapter redisTelemetry;
    private final DistanceCalculatorPort distanceCalculator;
    private final ExternalGeofencePort geofenceApi;

    @Override
    @Transactional
    public Mono<Trip> startTrip(UUID driverId, UUID vehicleId) {
        return Mono.zip(
            tripRepository.findByDriverIdAndStatus(driverId, "ONGOING").hasElement(),
            vehicleRepository.findById(vehicleId).switchIfEmpty(Mono.error(TripException.notFound(vehicleId))),
            driverPersistence.findById(driverId)
        ).flatMap(tuple -> {
            boolean hasActiveTrip = tuple.getT1();
            var vehicle = tuple.getT2();
            var driver = tuple.getT3();

            if (hasActiveTrip) return Mono.error(TripException.driverOccupied());
            if (!"AVAILABLE".equals(vehicle.getStatus())) return Mono.error(TripException.vehicleOccupied());
            if (driver.assignedVehicleId() == null || !driver.assignedVehicleId().equals(vehicleId)) 
                return Mono.error(TripException.vehicleNotAssigned());

            // Création Trip
            TripEntity entity = new TripEntity();
            entity.setId(UUID.randomUUID());
            entity.setDriverId(driverId);
            entity.setVehicleId(vehicleId);
            entity.setStartDate(LocalDate.now(ZoneOffset.UTC));
            entity.setStartTime(LocalTime.now(ZoneOffset.UTC));
            entity.setStatus("ONGOING");
            entity.setNew(true);

            // Verrouillage véhicule
            vehicle.setStatus("ON_TRIP");
            vehicle.setNew(false);

            return vehicleRepository.save(vehicle)
                    .then(tripRepository.save(entity))
                    .map(this::mapToDomain);
        });
    }

    @Override
    public Mono<Void> sendTelemetry(UUID tripId, Double lat, Double lng, Double speed) {
        return tripRepository.findById(tripId)
            .filter(t -> "ONGOING".equals(t.getStatus()))
            .switchIfEmpty(Mono.error(TripException.actionOnCompletedTrip()))
            .flatMap(trip -> {
                // 1. Redis (chaud)
                Mono<Void> redisTask = redisTelemetry.addPoint(tripId, lat, lng);
                
                // 2. Postgres Operational Params (Position Live)
                Mono<Void> sqlTask = operationalRepo.findByVehicleId(trip.getVehicleId())
                    .flatMap(op -> {
                        op.setCurrentLocation(lat + "," + lng);
                        op.setCurrentSpeed(BigDecimal.valueOf(speed != null ? speed : 0.0));
                        op.setTimestamp(Instant.now());
                        return operationalRepo.save(op);
                    }).then();

                // 3. Geofence Check (Fire & Forget)
                geofenceApi.checkPointInZone(null, lat, lng).subscribe(); 

                return Mono.when(redisTask, sqlTask);
            });
    }

    @Override
    @Transactional
    public Mono<Trip> endTrip(UUID tripId) {
        return tripRepository.findById(tripId)
            .filter(t -> "ONGOING".equals(t.getStatus()))
            .flatMap(trip -> redisTelemetry.getTripPath(tripId).collectList()
                .flatMap(points -> {
                    Double distance = distanceCalculator.calculateTotalDistanceKm(points);
                    
                    // Mise à jour Trip
                    trip.setStatus("COMPLETED");
                    trip.setEndDate(LocalDate.now(ZoneOffset.UTC));
                    trip.setEndTime(LocalTime.now(ZoneOffset.UTC));
                    trip.setDistanceKm(distance);
                    trip.setNew(false);

                    // Libération véhicule + Mise à jour Odomètre
                    return vehicleRepository.findById(trip.getVehicleId())
                        .flatMap(v -> { v.setStatus("AVAILABLE"); v.setNew(false); return vehicleRepository.save(v); })
                        .then(operationalRepo.findByVehicleId(trip.getVehicleId()))
                        .flatMap(op -> {
                            double currentOdo = op.getOdometerReading() != null ? op.getOdometerReading().doubleValue() : 0.0;
                            op.setOdometerReading(BigDecimal.valueOf(currentOdo + distance));
                            op.setMileage(BigDecimal.valueOf(currentOdo + distance));
                            return operationalRepo.save(op);
                        })
                        .then(tripRepository.save(trip))
                        .doOnSuccess(t -> redisTelemetry.clearTripPath(tripId).subscribe());
                }))
            .map(this::mapToDomain);
    }

    @Override public Mono<Trip> getMyActiveTrip(UUID d) { return tripRepository.findByDriverIdAndStatus(d, "ONGOING").map(this::mapToDomain); }
    @Override public Flux<Trip> getMyTripHistory(UUID d) { return tripRepository.findAll().filter(t -> d.equals(t.getDriverId())).map(this::mapToDomain); }
    @Override public Mono<Trip> getTripById(UUID id) { return tripRepository.findById(id).map(this::mapToDomain); }
    @Override public Flux<Trip> getManagerTrips(UUID m, UUID f) { return tripRepository.findAll().map(this::mapToDomain); } // Simplifié

    private Trip mapToDomain(TripEntity e) {
        return new Trip(e.getId(), e.getVehicleId(), e.getDriverId(), e.getStatus(), e.getStartDate(), e.getStartTime(), e.getEndDate(), e.getEndTime(), e.getDistanceKm(), e.getDurationMinutes());
    }
}