package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.TripException;
import com.yowyob.fleet.domain.model.Trip;
import com.yowyob.fleet.domain.model.TripDetail;
import com.yowyob.fleet.domain.ports.in.ManageTripUseCase;
import com.yowyob.fleet.domain.ports.out.*;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.RedisTelemetryAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripDetailEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.OperationalParameterR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripDetailR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class TripService implements ManageTripUseCase {

    private static final Logger log = LoggerFactory.getLogger(
            TripService.class);

    private final TripR2dbcRepository tripRepository;
    private final TripDetailR2dbcRepository detailRepository;
    private final VehicleLocalR2dbcRepository vehicleRepository;
    private final DriverPersistencePort driverPersistence;
    private final OperationalParameterR2dbcRepository operationalRepo;
    private final RedisTelemetryAdapter redisTelemetry;
    private final DistanceCalculatorPort distanceCalculator;
    private final ExternalGeofencePort geofenceApi;
    private final DatabaseClient db;

    // ── Création d'un trajet (Manager) ────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Trip> createTrip(CreateTripCommand cmd) {
        return Mono.zip(
                vehicleRepository
                        .findById(cmd.vehicleId())
                        .switchIfEmpty(
                                Mono.error(TripException.notFound(cmd.vehicleId()))),
                driverPersistence.findById(cmd.driverId())).flatMap(tuple -> {
                    var vehicle = tuple.getT1();
                    if (!"AVAILABLE".equals(vehicle.getStatus())) {
                        return Mono.error(TripException.vehicleOccupied());
                    }

                    return db
                            .sql("SELECT fleet.generate_trip_code() AS code")
                            .map(row -> row.get("code", String.class))
                            .one()
                            .flatMap(tripCode -> {
                                TripEntity entity = buildTripEntity(tripCode, cmd);

                                vehicle.setStatus("ON_TRIP");
                                vehicle.setNew(false);

                                return vehicleRepository
                                        .save(vehicle)
                                        .then(tripRepository.save(entity))
                                        .flatMap(saved -> saveDetails(saved.getId(), cmd.details()).then(
                                                loadTripWithDetails(saved.getId())));
                            });
                });
    }

    // ── Enregistrement du retour ──────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Trip> registerReturn(RegisterReturnCommand cmd) {
        return tripRepository
                .findByTripCode(cmd.tripCode())
                .switchIfEmpty(
                        Mono.error(TripException.notFoundByCode(cmd.tripCode())))
                .flatMap(trip -> {
                    if ("COMPLETED".equals(trip.getStatus()) ||
                            "CANCELLED".equals(trip.getStatus())) {
                        return Mono.error(TripException.actionOnCompletedTrip());
                    }

                    // Calculs automatiques
                    BigDecimal computedDistance = null;
                    if (cmd.returnKmIndex() != null &&
                            trip.getDepartureKmIndex() != null) {
                        computedDistance = cmd
                                .returnKmIndex()
                                .subtract(trip.getDepartureKmIndex());
                    }
                    BigDecimal computedFuel = null;
                    if (trip.getDepartureFuelIndex() != null &&
                            cmd.returnFuelIndex() != null) {
                        computedFuel = trip
                                .getDepartureFuelIndex()
                                .subtract(cmd.returnFuelIndex());
                    }
                    Integer durationMinutes = null;
                    if (trip.getStartDate() != null && trip.getStartTime() != null) {
                        LocalDateTime depart = LocalDateTime.of(
                                trip.getStartDate(),
                                trip.getStartTime());
                        LocalDateTime retour = LocalDateTime.of(
                                cmd.returnDate(),
                                cmd.returnTime());
                        durationMinutes = (int) java.time.temporal.ChronoUnit.MINUTES.between(
                                depart,
                                retour);
                    }

                    trip.setStatus("COMPLETED");
                    trip.setEndDate(cmd.returnDate());
                    trip.setEndTime(cmd.returnTime());
                    trip.setReturnLocation(cmd.returnLocation());
                    trip.setReturnKmIndex(cmd.returnKmIndex());
                    trip.setReturnFuelIndex(cmd.returnFuelIndex());
                    trip.setReturnRegisteredAt(Instant.now());
                    trip.setComputedDistanceKm(computedDistance);
                    trip.setComputedFuelConsumed(computedFuel);
                    trip.setDistanceKm(
                            computedDistance != null
                                    ? computedDistance.doubleValue()
                                    : null);
                    trip.setDurationMinutes(durationMinutes);
                    trip.setNew(false);

                    // Libérer véhicule
                    Mono<Void> vehicleFree = vehicleRepository
                            .findById(trip.getVehicleId())
                            .flatMap(v -> {
                                v.setStatus("AVAILABLE");
                                v.setNew(false);
                                return vehicleRepository.save(v);
                            })
                            .then();

                    // Mise à jour odomètre
                    final BigDecimal dist = computedDistance;
                    Mono<Void> odometer = (dist != null)
                            ? operationalRepo
                                    .findByVehicleId(trip.getVehicleId())
                                    .flatMap(op -> {
                                        double cur = op.getOdometerReading() != null
                                                ? op.getOdometerReading().doubleValue()
                                                : 0;
                                        op.setOdometerReading(
                                                BigDecimal.valueOf(cur + dist.doubleValue()));
                                        op.setMileage(
                                                BigDecimal.valueOf(cur + dist.doubleValue()));
                                        return operationalRepo.save(op);
                                    })
                                    .then()
                            : Mono.empty();

                    // Mise à jour quantités retour dans les détails
                    Mono<Void> detailsUpdate = (cmd.detailUpdates() != null &&
                            !cmd.detailUpdates().isEmpty())
                                    ? Flux.fromIterable(cmd.detailUpdates())
                                            .flatMap(du -> detailRepository
                                                    .findById(du.detailId())
                                                    .flatMap(de -> {
                                                        de.setReturnQuantity(du.returnQuantity());
                                                        de.setNew(false);
                                                        return detailRepository.save(de);
                                                    }))
                                            .then()
                                    : Mono.empty();

                    final TripEntity tripToSave = trip;
                    return Mono.when(vehicleFree, odometer, detailsUpdate)
                            .then(tripRepository.save(tripToSave))
                            .flatMap(saved -> loadTripWithDetails(saved.getId()));
                });
    }

    // ── Recherche par code ────────────────────────────────────────────────────

    @Override
    public Mono<Trip> getTripByCode(String tripCode) {
        return tripRepository
                .findByTripCode(tripCode)
                .switchIfEmpty(Mono.error(TripException.notFoundByCode(tripCode)))
                .flatMap(e -> loadTripWithDetails(e.getId()));
    }

    // ── Changer de conducteur ─────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Trip> updateTripDriver(
            UUID tripId,
            UUID newDriverId,
            UUID managerId) {
        return tripRepository
                .findById(tripId)
                .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
                .flatMap(trip -> {
                    if (!"SCHEDULED".equals(trip.getStatus())) {
                        return Mono.error(
                                new RuntimeException(
                                        "Changement de conducteur impossible : trajet déjà démarré."));
                    }
                    return driverPersistence
                            .findById(newDriverId)
                            .flatMap(driver -> {
                                trip.setDriverId(newDriverId);
                                trip.setNew(false);
                                return tripRepository
                                        .save(trip)
                                        .flatMap(saved -> loadTripWithDetails(saved.getId()));
                            });
                });
    }

    // ── Annulation ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Trip> cancelTrip(UUID tripId, String reason, UUID managerId) {
        return tripRepository
                .findById(tripId)
                .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
                .flatMap(trip -> {
                    if ("COMPLETED".equals(trip.getStatus()) ||
                            "CANCELLED".equals(trip.getStatus())) {
                        return Mono.error(TripException.actionOnCompletedTrip());
                    }

                    boolean wasOnTrip = "DEPARTED".equals(trip.getStatus()) ||
                            "SCHEDULED".equals(trip.getStatus());
                    trip.setStatus("CANCELLED");
                    trip.setCancelReason(reason);
                    trip.setCancelledAt(Instant.now());
                    trip.setNew(false);

                    Mono<Void> freeVehicle = wasOnTrip
                            ? vehicleRepository
                                    .findById(trip.getVehicleId())
                                    .flatMap(v -> {
                                        v.setStatus("AVAILABLE");
                                        v.setNew(false);
                                        return vehicleRepository.save(v);
                                    })
                                    .then()
                            : Mono.empty();

                    return freeVehicle
                            .then(tripRepository.save(trip))
                            .flatMap(saved -> loadTripWithDetails(saved.getId()));
                });
    }

    // ── Listes Manager ────────────────────────────────────────────────────────

    @Override
    public Flux<Trip> getManagerTrips(UUID managerId, UUID fleetId) {
        Flux<TripEntity> source = (fleetId != null)
                ? tripRepository.findAllByCreatedByAndFleetId(managerId, fleetId)
                : tripRepository.findAllByCreatedBy(managerId);
        return source.flatMap(e -> loadTripWithDetails(e.getId()));
    }

    @Override
    public Mono<Trip> getTripById(UUID id) {
        return tripRepository
                .findById(id)
                .switchIfEmpty(Mono.error(TripException.notFound(id)))
                .flatMap(e -> loadTripWithDetails(e.getId()));
    }

    // ── Télémétrie (conservé) ─────────────────────────────────────────────────

    @Override
    public Mono<Void> sendTelemetry(
            UUID tripId,
            Double lat,
            Double lng,
            Double speed) {
        return tripRepository
                .findById(tripId)
                .filter(
                        t -> "DEPARTED".equals(t.getStatus()) ||
                                "RETURNING".equals(t.getStatus()))
                .switchIfEmpty(Mono.error(TripException.actionOnCompletedTrip()))
                .flatMap(trip -> {
                    Mono<Void> redisTask = redisTelemetry.addPoint(
                            tripId,
                            lat,
                            lng);
                    Mono<Void> sqlTask = operationalRepo
                            .findByVehicleId(trip.getVehicleId())
                            .flatMap(op -> {
                                op.setCurrentSpeed(
                                        BigDecimal.valueOf(speed != null ? speed : 0.0));
                                op.setTimestamp(Instant.now());
                                return operationalRepo.save(op);
                            })
                            .then();
                    geofenceApi.checkPointInZone(null, lat, lng).subscribe();
                    return Mono.when(redisTask, sqlTask);
                });
    }

    @Override
    public Mono<Trip> getMyActiveTrip(UUID driverId) {
        return tripRepository
                .findByDriverIdAndStatus(driverId, "DEPARTED")
                .flatMap(e -> loadTripWithDetails(e.getId()));
    }

    @Override
    public Flux<Trip> getMyTripHistory(UUID driverId) {
        return tripRepository
                .findAllByDriverId(driverId)
                .flatMap(e -> loadTripWithDetails(e.getId()));
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    private TripEntity buildTripEntity(String tripCode, CreateTripCommand cmd) {
        TripEntity e = new TripEntity();
        e.setId(UUID.randomUUID());
        e.setTripCode(tripCode);
        e.setVehicleId(cmd.vehicleId());
        e.setDriverId(cmd.driverId());
        e.setFleetId(cmd.fleetId());
        e.setCreatedBy(cmd.managerId());
        e.setStatus("SCHEDULED");
        e.setStartDate(cmd.startDate());
        e.setStartTime(cmd.startTime());
        e.setDepartureLocation(cmd.departureLocation());
        e.setDepartureKmIndex(cmd.departureKmIndex());
        e.setDepartureFuelIndex(cmd.departureFuelIndex());
        e.setMissionObject(cmd.missionObject());
        e.setMissionCost(cmd.missionCost());
        e.setRateType(cmd.rateType());
        e.setScheduledReturnDatetime(cmd.scheduledReturnDatetime());
        e.setNew(true);
        return e;
    }

    private Mono<Void> saveDetails(UUID tripId, List<TripDetailInput> inputs) {
        if (inputs == null || inputs.isEmpty())
            return Mono.empty();
        List<TripDetailEntity> entities = inputs
                .stream()
                .map(d -> {
                    TripDetailEntity de = new TripDetailEntity();
                    de.setId(UUID.randomUUID());
                    de.setTripId(tripId);
                    de.setItemType(d.itemType());
                    de.setDescription(d.description());
                    de.setQuantity(d.quantity());
                    de.setWeight(d.weight());
                    de.setDepartureQuantity(
                            d.departureQuantity() != null
                                    ? d.departureQuantity()
                                    : d.quantity());
                    de.setNew(true);
                    return de;
                })
                .collect(Collectors.toList());
        return detailRepository.saveAll(entities).then();
    }

    private Mono<Trip> loadTripWithDetails(UUID tripId) {
        return tripRepository.findById(tripId).flatMap(entity -> detailRepository
                .findAllByTripIdOrderBySortOrder(entity.getId())
                .collectList()
                .map(details -> mapToDomain(entity, details)));
    }

    private Trip mapToDomain(
            TripEntity e,
            List<TripDetailEntity> detailEntities) {
        List<TripDetail> details = detailEntities
                .stream()
                .map(d -> new TripDetail(
                        d.getId(),
                        d.getTripId(),
                        TripDetail.ItemType.valueOf(d.getItemType()),
                        d.getDescription(),
                        d.getQuantity(),
                        d.getWeight(),
                        d.getDepartureQuantity(),
                        d.getReturnQuantity(),
                        d.getSortOrder()))
                .collect(Collectors.toList());

        Trip.RateType rateType = null;
        if (e.getRateType() != null) {
            try {
                rateType = Trip.RateType.valueOf(e.getRateType());
            } catch (Exception ex) {
                /* ignore */
            }
        }

        Trip.Status status = null;
        if (e.getStatus() != null) {
            try {
                status = Trip.Status.valueOf(e.getStatus());
            } catch (Exception ex) {
                /* ignore */
            }
        }

        return new Trip(
                e.getId(),
                e.getTripCode(),
                e.getVehicleId(),
                e.getDriverId(),
                e.getFleetId(),
                e.getCreatedBy(),
                status,
                e.getStartDate(),
                e.getStartTime(),
                e.getDepartureLocation(),
                e.getDepartureKmIndex(),
                e.getDepartureFuelIndex(),
                e.getEndDate(),
                e.getEndTime(),
                e.getReturnLocation(),
                e.getReturnKmIndex(),
                e.getReturnFuelIndex(),
                e.getReturnRegisteredAt(),
                e.getScheduledReturnDatetime(),
                e.getMissionObject(),
                e.getMissionCost(),
                rateType,
                e.getDistanceKm(),
                e.getDurationMinutes(),
                e.getComputedDistanceKm(),
                e.getComputedFuelConsumed(),
                e.getCancelReason(),
                e.getCancelledAt(),
                details);
    }

    @Override
    @Transactional
    public Mono<Trip> startTrip(UUID tripId, BigDecimal departureKmIndex, BigDecimal departureFuelIndex,
            String departureLocation) {
        return tripRepository.findById(tripId)
                .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
                .flatMap(trip -> {
                    if (!"SCHEDULED".equals(trip.getStatus())) {
                        return Mono.error(new RuntimeException(
                                "Trip cannot be started in its current status: " + trip.getStatus()));
                    }
                    trip.setStatus("DEPARTED");
                    if (departureKmIndex != null)
                        trip.setDepartureKmIndex(departureKmIndex);
                    if (departureFuelIndex != null)
                        trip.setDepartureFuelIndex(departureFuelIndex);
                    if (departureLocation != null)
                        trip.setDepartureLocation(departureLocation);
                    trip.setNew(false);

                    // Also update vehicle state when trip actually departs
                    return vehicleRepository.findById(trip.getVehicleId())
                            .flatMap(v -> {
                                v.setStatus("ON_TRIP");
                                v.setNew(false);
                                return vehicleRepository.save(v);
                            })
                            .then(tripRepository.save(trip))
                            .flatMap(saved -> loadTripWithDetails(saved.getId()));
                });
    }

    @Override
    @Transactional
    public Mono<Trip> returningTrip(UUID tripId) {
        return tripRepository.findById(tripId)
                .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
                .flatMap(trip -> {
                    if (!"DEPARTED".equals(trip.getStatus())) {
                        return Mono.error(new RuntimeException(
                                "Trip cannot transition to returning from status: " + trip.getStatus()));
                    }
                    trip.setStatus("RETURNING");
                    trip.setNew(false);
                    return tripRepository.save(trip).flatMap(saved -> loadTripWithDetails(saved.getId()));
                });
    }

    @Override
    @Transactional
    public Mono<Trip> completeTrip(UUID tripId, BigDecimal returnKmIndex, BigDecimal returnFuelIndex,
            String returnLocation) {
        return tripRepository.findById(tripId)
                .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
                .flatMap(trip -> {
                    return registerReturn(new RegisterReturnCommand(
                            trip.getTripCode(),
                            LocalDate.now(),
                            LocalTime.now(),
                            returnLocation != null ? returnLocation : "Destination",
                            returnKmIndex,
                            returnFuelIndex,
                            List.of()));
                });
    }
}
