package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.TripException;
import com.yowyob.fleet.domain.model.Trip;
import com.yowyob.fleet.domain.model.TripDetail;
import com.yowyob.fleet.domain.ports.in.ManageTripUseCase;
import com.yowyob.fleet.domain.ports.out.*;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.RedisTelemetryAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripDetailEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripMissionSubmissionEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.OperationalParameterR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripDetailR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripMissionSubmissionR2dbcRepository;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class TripService implements ManageTripUseCase {

    private static final Logger log = LoggerFactory.getLogger(
        TripService.class
    );

    private final TripR2dbcRepository tripRepository;
    private final TripDetailR2dbcRepository detailRepository;
    private final TripMissionSubmissionR2dbcRepository submissionRepository;
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
                    Mono.error(TripException.notFound(cmd.vehicleId()))
                ),
            driverPersistence.findById(cmd.driverId())
        ).flatMap(tuple -> {
            var vehicle = tuple.getT1();
            if (!"AVAILABLE".equals(vehicle.getStatus())) {
                return Mono.error(TripException.vehicleOccupied());
            }

            UUID fleetId = cmd.fleetId() != null ? cmd.fleetId() : vehicle.getFleetId();
            if (fleetId == null) {
                return Mono.error(new IllegalArgumentException("fleetId introuvable pour ce véhicule"));
            }
            CreateTripCommand resolvedCmd = new CreateTripCommand(
                    cmd.vehicleId(),
                    cmd.driverId(),
                    fleetId,
                    cmd.managerId(),
                    cmd.startDate(),
                    cmd.startTime(),
                    cmd.departureLocation(),
                    cmd.departureLat(),
                    cmd.departureLng(),
                    cmd.departureKmIndex(),
                    cmd.departureFuelIndex(),
                    cmd.missionObject(),
                    cmd.missionCost(),
                    cmd.missionCostCurrency(),
                    cmd.rateType(),
                    cmd.scheduledReturnDatetime(),
                    cmd.details()
            );

            return assertNoActiveTripForDriver(cmd.driverId(), null)
                .then(assertNoActiveTripForVehicle(cmd.vehicleId(), null))
                .then(saveNewTrip(resolvedCmd));
        });
    }

    private Mono<Trip> saveNewTrip(CreateTripCommand cmd) {
        return generateTripCode()
                .flatMap(tripCode -> persistTrip(tripCode, cmd))
                .onErrorResume(DuplicateKeyException.class, ex -> generateTripCode()
                        .flatMap(tripCode -> persistTrip(tripCode, cmd)));
    }

    private Mono<String> generateTripCode() {
        return db.sql("SELECT fleet.generate_trip_code() AS code")
                .map(row -> row.get("code", String.class))
                .one();
    }

    private Mono<Trip> persistTrip(String tripCode, CreateTripCommand cmd) {
        TripEntity entity = buildTripEntity(tripCode, cmd);
        return tripRepository.save(entity)
                .flatMap(saved -> saveDetails(saved.getId(), cmd.details())
                        .then(loadTripWithDetails(saved.getId())));
    }

    // ── Lancement effectif d'un trajet planifié ───────────────────────────────

    @Override
    @Transactional
    public Mono<Trip> startTrip(UUID tripId, UUID managerId) {
        return tripRepository
            .findById(tripId)
            .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
            .flatMap(trip -> {
                if (!managerId.equals(trip.getCreatedBy())) {
                    return Mono.error(TripException.forbidden());
                }
                if (!"SCHEDULED".equals(trip.getStatus())) {
                    return Mono.error(TripException.invalidStartState());
                }

                return vehicleRepository
                    .findById(trip.getVehicleId())
                    .switchIfEmpty(
                        Mono.error(TripException.notFound(trip.getVehicleId()))
                    )
                    .flatMap(vehicle -> {
                        if (!"AVAILABLE".equals(vehicle.getStatus())) {
                            return Mono.error(TripException.vehicleOccupied());
                        }

                        LocalDateTime now = LocalDateTime.now();
                        trip.setStatus("DEPARTED");
                        trip.setStartDate(now.toLocalDate());
                        trip.setStartTime(now.toLocalTime().withNano(0));
                        trip.setDepartureRegisteredAt(Instant.now());
                        trip.setNew(false);

                        vehicle.setStatus("ON_TRIP");
                        vehicle.setNew(false);

                        return vehicleRepository
                            .save(vehicle)
                            .then(
                                linkDriverVehicleForTrip(
                                    trip.getDriverId(),
                                    trip.getVehicleId()
                                )
                            )
                            .then(tripRepository.save(trip))
                            .flatMap(saved -> loadTripWithDetails(saved.getId()));
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
                Mono.error(TripException.notFoundByCode(cmd.tripCode()))
            )
            .flatMap(trip -> {
                if (
                    "COMPLETED".equals(trip.getStatus()) ||
                    "CANCELLED".equals(trip.getStatus())
                ) {
                    return Mono.error(TripException.actionOnCompletedTrip());
                }

                // Calculs automatiques
                BigDecimal computedDistance = null;
                if (
                    cmd.returnKmIndex() != null &&
                    trip.getDepartureKmIndex() != null
                ) {
                    computedDistance = cmd
                        .returnKmIndex()
                        .subtract(trip.getDepartureKmIndex());
                }
                BigDecimal computedFuel = null;
                if (
                    trip.getDepartureFuelIndex() != null &&
                    cmd.returnFuelIndex() != null
                ) {
                    computedFuel = trip
                        .getDepartureFuelIndex()
                        .subtract(cmd.returnFuelIndex());
                }
                Integer durationMinutes = null;
                if (
                    trip.getStartDate() != null && trip.getStartTime() != null
                ) {
                    LocalDateTime depart = LocalDateTime.of(
                        trip.getStartDate(),
                        trip.getStartTime()
                    );
                    LocalDateTime retour = LocalDateTime.of(
                        cmd.returnDate(),
                        cmd.returnTime()
                    );
                    durationMinutes =
                        (int) java.time.temporal.ChronoUnit.MINUTES.between(
                            depart,
                            retour
                        );
                }

                trip.setStatus("COMPLETED");
                trip.setEndDate(cmd.returnDate());
                trip.setEndTime(cmd.returnTime());
                trip.setReturnLocation(cmd.returnLocation());
                trip.setReturnLat(cmd.returnLat());
                trip.setReturnLng(cmd.returnLng());
                trip.setReturnKmIndex(cmd.returnKmIndex());
                trip.setReturnFuelIndex(cmd.returnFuelIndex());
                trip.setReturnRegisteredAt(Instant.now());
                trip.setComputedDistanceKm(computedDistance);
                trip.setComputedFuelConsumed(computedFuel);
                trip.setDistanceKm(
                    computedDistance != null
                        ? computedDistance.doubleValue()
                        : null
                );
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
                              double cur =
                                  op.getOdometerReading() != null
                                      ? op.getOdometerReading().doubleValue()
                                      : 0;
                              op.setOdometerReading(
                                  BigDecimal.valueOf(cur + dist.doubleValue())
                              );
                              op.setMileage(
                                  BigDecimal.valueOf(cur + dist.doubleValue())
                              );
                              return operationalRepo.save(op);
                          })
                          .then()
                    : Mono.empty();

                // Mise à jour quantités retour dans les détails
                Mono<Void> detailsUpdate = (cmd.detailUpdates() != null &&
                    !cmd.detailUpdates().isEmpty())
                    ? Flux.fromIterable(cmd.detailUpdates())
                          .flatMap(du ->
                              detailRepository
                                  .findById(du.detailId())
                                  .flatMap(de -> {
                                      de.setReturnQuantity(du.returnQuantity());
                                      de.setNew(false);
                                      return detailRepository.save(de);
                                  })
                          )
                          .then()
                    : Mono.empty();

                final TripEntity tripToSave = trip;
                Mono<Void> unlinkPair = unlinkDriverVehicleForTrip(
                    trip.getDriverId(),
                    trip.getVehicleId()
                );
                return Mono.when(vehicleFree, odometer, detailsUpdate, unlinkPair)
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
        UUID managerId
    ) {
        return tripRepository
            .findById(tripId)
            .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
            .flatMap(trip -> {
                if (!isModifiable(trip.getStatus())) {
                    return Mono.error(TripException.tripNotModifiable());
                }
                return assertNoActiveTripForDriver(newDriverId, tripId)
                    .then(
                        driverPersistence
                            .findById(newDriverId)
                            .switchIfEmpty(
                                Mono.error(
                                    TripException.notFound(newDriverId)
                                )
                            )
                    )
                    .flatMap(driver -> {
                        trip.setDriverId(newDriverId);
                        trip.setNew(false);
                        return tripRepository
                            .save(trip)
                            .flatMap(saved ->
                                loadTripWithDetails(saved.getId())
                            );
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
                if (
                    "COMPLETED".equals(trip.getStatus()) ||
                    "CANCELLED".equals(trip.getStatus())
                ) {
                    return Mono.error(TripException.actionOnCompletedTrip());
                }

                boolean wasOnTrip =
                    "DEPARTED".equals(trip.getStatus()) ||
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

                Mono<Void> unlinkPair = wasOnTrip
                    ? unlinkDriverVehicleForTrip(
                          trip.getDriverId(),
                          trip.getVehicleId()
                      )
                    : Mono.empty();

                return freeVehicle
                    .then(unlinkPair)
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
    @Transactional
    public Mono<Void> deleteTrip(UUID tripId, UUID managerId) {
        return tripRepository
            .findById(tripId)
            .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
            .flatMap(trip -> {
                if (!managerId.equals(trip.getCreatedBy())) {
                    return Mono.error(new AccessDeniedException("Ce trajet ne vous appartient pas."));
                }
                String status = trip.getStatus();
                if ("SCHEDULED".equals(status) || "CANCELLED".equals(status)) {
                    return tripRepository.deleteById(tripId);
                }
                return Mono.error(new IllegalStateException(
                        "Seuls les trajets SCHEDULED ou CANCELLED peuvent être supprimés. Annulez-le d'abord."));
            });
    }

    @Override
    public Mono<Trip> getTripById(UUID id) {
        return tripRepository
            .findById(id)
            .switchIfEmpty(Mono.error(TripException.notFound(id)))
            .flatMap(e -> loadTripWithDetails(e.getId()));
    }

    @Override
    public Flux<Trip> getOpenTrips(UUID managerId) {
        return tripRepository
            .findOpenTripsByCreatedBy(managerId)
            .flatMap(e -> loadTripWithDetails(e.getId()));
    }

    @Override
    @Transactional
    public Mono<Trip> updateTripVehicle(
        UUID tripId,
        UUID newVehicleId,
        UUID managerId
    ) {
        return tripRepository
            .findById(tripId)
            .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
            .flatMap(trip -> {
                if (!isModifiable(trip.getStatus())) {
                    return Mono.error(TripException.tripNotModifiable());
                }
                if (newVehicleId.equals(trip.getVehicleId())) {
                    return loadTripWithDetails(tripId);
                }
                return assertNoActiveTripForVehicle(newVehicleId, tripId)
                    .then(vehicleRepository.findById(newVehicleId))
                    .switchIfEmpty(
                        Mono.error(TripException.notFound(newVehicleId))
                    )
                    .flatMap(newVehicle -> {
                        if (!"AVAILABLE".equals(newVehicle.getStatus())) {
                            return Mono.error(TripException.vehicleOccupied());
                        }
                        UUID oldVehicleId = trip.getVehicleId();
                        trip.setVehicleId(newVehicleId);
                        trip.setNew(false);
                        newVehicle.setStatus("ON_TRIP");
                        newVehicle.setNew(false);
                        return vehicleRepository
                            .save(newVehicle)
                            .then(
                                vehicleRepository
                                    .findById(oldVehicleId)
                                    .flatMap(old -> {
                                        old.setStatus("AVAILABLE");
                                        old.setNew(false);
                                        return vehicleRepository.save(old);
                                    })
                            )
                            .then(tripRepository.save(trip))
                            .flatMap(saved ->
                                loadTripWithDetails(saved.getId())
                            );
                    });
            });
    }

    @Override
    @Transactional
    public Mono<Trip> updateTrip(UUID tripId, UpdateTripCommand cmd) {
        return tripRepository
            .findById(tripId)
            .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
            .flatMap(trip -> {
                if (!isModifiable(trip.getStatus())) {
                    return Mono.error(TripException.tripNotModifiable());
                }
                Mono<Void> driverCheck = (cmd.driverId() != null &&
                    !cmd.driverId().equals(trip.getDriverId()))
                    ? assertNoActiveTripForDriver(cmd.driverId(), tripId)
                    : Mono.empty();
                Mono<Void> vehicleCheck = (cmd.vehicleId() != null &&
                    !cmd.vehicleId().equals(trip.getVehicleId()))
                    ? updateTripVehicle(tripId, cmd.vehicleId(), cmd.managerId())
                          .then()
                    : Mono.empty();

                return driverCheck
                    .then(vehicleCheck)
                    .then(
                        Mono.defer(() -> {
                            if (
                                cmd.driverId() != null &&
                                !cmd.driverId().equals(trip.getDriverId())
                            ) {
                                trip.setDriverId(cmd.driverId());
                            }
                            if (cmd.startDate() != null) trip.setStartDate(
                                cmd.startDate()
                            );
                            if (cmd.startTime() != null) trip.setStartTime(
                                cmd.startTime()
                            );
                            if (cmd.departureLocation() != null) trip.setDepartureLocation(
                                cmd.departureLocation()
                            );
                            if (cmd.departureLat() != null) trip.setDepartureLat(
                                cmd.departureLat()
                            );
                            if (cmd.departureLng() != null) trip.setDepartureLng(
                                cmd.departureLng()
                            );
                            if (cmd.departureKmIndex() != null) trip.setDepartureKmIndex(
                                cmd.departureKmIndex()
                            );
                            if (cmd.departureFuelIndex() != null) trip.setDepartureFuelIndex(
                                cmd.departureFuelIndex()
                            );
                            if (cmd.missionObject() != null) trip.setMissionObject(
                                cmd.missionObject()
                            );
                            if (cmd.missionCost() != null) trip.setMissionCost(
                                cmd.missionCost()
                            );
                            if (cmd.missionCostCurrency() != null) trip.setMissionCostCurrency(
                                cmd.missionCostCurrency()
                            );
                            trip.setNew(false);
                            return tripRepository
                                .save(trip)
                                .flatMap(saved ->
                                    loadTripWithDetails(saved.getId())
                                );
                        })
                    );
            });
    }

    @Override
    @Transactional
    public Mono<UUID> submitMissionComplement(
        UUID tripId,
        UUID driverId,
        MissionSubmissionInput input
    ) {
        return tripRepository
            .findById(tripId)
            .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
            .flatMap(trip -> {
                if (!driverId.equals(trip.getDriverId())) {
                    return Mono.error(TripException.vehicleNotAssigned());
                }
                if (!isModifiable(trip.getStatus())) {
                    return Mono.error(TripException.tripNotModifiable());
                }
                TripMissionSubmissionEntity sub = new TripMissionSubmissionEntity();
                sub.setId(UUID.randomUUID());
                sub.setTripId(tripId);
                sub.setSubmittedBy(driverId);
                sub.setItemType(input.itemType());
                sub.setDescription(input.description());
                sub.setQuantity(input.quantity());
                sub.setWeight(input.weight());
                sub.setNotes(input.notes());
                sub.setStatus("PENDING");
                sub.setCreatedAt(Instant.now());
                sub.setNew(true);
                return submissionRepository.save(sub).map(
                    TripMissionSubmissionEntity::getId
                );
            });
    }

    @Override
    @Transactional
    public Mono<Trip> approveMissionSubmission(
        UUID submissionId,
        UUID managerId
    ) {
        return submissionRepository
            .findByIdAndStatus(submissionId, "PENDING")
            .switchIfEmpty(
                Mono.error(
                    new RuntimeException("Complément introuvable ou déjà traité.")
                )
            )
            .flatMap(sub ->
                tripRepository
                    .findById(sub.getTripId())
                    .flatMap(trip -> {
                        TripDetailEntity detail = new TripDetailEntity();
                        detail.setId(UUID.randomUUID());
                        detail.setTripId(sub.getTripId());
                        detail.setItemType(sub.getItemType());
                        detail.setDescription(
                            sub.getDescription() != null
                                ? sub.getDescription()
                                : sub.getNotes()
                        );
                        detail.setQuantity(
                            sub.getQuantity() != null ? sub.getQuantity() : 1
                        );
                        detail.setWeight(sub.getWeight());
                        detail.setDepartureQuantity(
                            sub.getQuantity() != null ? sub.getQuantity() : 1
                        );
                        detail.setNew(true);
                        sub.setStatus("APPROVED");
                        sub.setReviewedBy(managerId);
                        sub.setReviewedAt(Instant.now());
                        sub.setNew(false);
                        return detailRepository
                            .save(detail)
                            .then(submissionRepository.save(sub))
                            .then(loadTripWithDetails(trip.getId()));
                    })
            );
    }

    @Override
    @Transactional
    public Mono<Void> rejectMissionSubmission(
        UUID submissionId,
        UUID managerId
    ) {
        return submissionRepository
            .findByIdAndStatus(submissionId, "PENDING")
            .switchIfEmpty(
                Mono.error(
                    new RuntimeException("Complément introuvable ou déjà traité.")
                )
            )
            .flatMap(sub -> {
                sub.setStatus("REJECTED");
                sub.setReviewedBy(managerId);
                sub.setReviewedAt(Instant.now());
                sub.setNew(false);
                return submissionRepository.save(sub).then();
            });
    }

    // ── Télémétrie (conservé) ─────────────────────────────────────────────────

    @Override
    public Mono<Void> sendTelemetry(
        UUID tripId,
        Double lat,
        Double lng,
        Double speed
    ) {
        return tripRepository
            .findById(tripId)
            .filter(
                t ->
                    "DEPARTED".equals(t.getStatus()) ||
                    "RETURNING".equals(t.getStatus())
            )
            .switchIfEmpty(Mono.error(TripException.actionOnCompletedTrip()))
            .flatMap(trip -> {
                Mono<Void> redisTask = redisTelemetry.addPoint(
                    tripId,
                    lat,
                    lng
                );
                Mono<Void> sqlTask = operationalRepo
                    .findByVehicleId(trip.getVehicleId())
                    .flatMap(op -> {
                        op.setCurrentSpeed(
                            BigDecimal.valueOf(speed != null ? speed : 0.0)
                        );
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

    private boolean isModifiable(String status) {
        return (
            "DEPARTED".equals(status) ||
            "RETURNING".equals(status) ||
            "SCHEDULED".equals(status)
        );
    }

    private Mono<Void> assertNoActiveTripForDriver(
        UUID driverId,
        UUID excludeTripId
    ) {
        Mono<Boolean> check =
            excludeTripId == null
                ? tripRepository.existsActiveTripForDriver(driverId)
                : tripRepository.existsActiveTripForDriverExcluding(
                      driverId,
                      excludeTripId
                  );
        return check.flatMap(busy ->
            Boolean.TRUE.equals(busy)
                ? Mono.error(TripException.driverOccupied())
                : Mono.empty()
        );
    }

    private Mono<Void> assertNoActiveTripForVehicle(
        UUID vehicleId,
        UUID excludeTripId
    ) {
        Mono<Boolean> check =
            excludeTripId == null
                ? tripRepository.existsActiveTripForVehicle(vehicleId)
                : tripRepository.existsActiveTripForVehicleExcluding(
                      vehicleId,
                      excludeTripId
                  );
        return check.flatMap(busy ->
            Boolean.TRUE.equals(busy)
                ? Mono.error(TripException.vehicleOccupied())
                : Mono.empty()
        );
    }

    /** Associe chauffeur et véhicule le temps d'un trajet actif. */
    private Mono<Void> linkDriverVehicleForTrip(UUID driverId, UUID vehicleId) {
        Mono<Void> clearDriverOldVehicle = driverPersistence
            .findById(driverId)
            .flatMap(driver -> {
                if (
                    driver.assignedVehicleId() != null &&
                    !driver.assignedVehicleId().equals(vehicleId)
                ) {
                    return vehicleRepository
                        .findById(driver.assignedVehicleId())
                        .flatMap(v -> {
                            v.setCurrentDriverId(null);
                            v.setNew(false);
                            return vehicleRepository.save(v);
                        })
                        .then(
                            driverPersistence.updateVehicleAssignment(
                                driverId,
                                null
                            )
                        );
                }
                return Mono.empty();
            });

        Mono<Void> clearVehicleOldDriver = driverPersistence
            .findByAssignedVehicleId(vehicleId)
            .flatMap(oldDriver -> {
                if (!oldDriver.userId().equals(driverId)) {
                    return driverPersistence
                        .updateVehicleAssignment(oldDriver.userId(), null)
                        .then(
                            vehicleRepository
                                .findById(vehicleId)
                                .flatMap(v -> {
                                    v.setCurrentDriverId(null);
                                    v.setNew(false);
                                    return vehicleRepository.save(v);
                                })
                                .then()
                        );
                }
                return Mono.empty();
            })
            .onErrorResume(e -> Mono.empty());

        Mono<Void> setLinks = vehicleRepository
            .findById(vehicleId)
            .flatMap(v -> {
                v.setCurrentDriverId(driverId);
                v.setNew(false);
                return vehicleRepository.save(v);
            })
            .then(driverPersistence.updateVehicleAssignment(driverId, vehicleId));

        return clearDriverOldVehicle
            .then(clearVehicleOldDriver)
            .then(setLinks);
    }

    /** Dissocie chauffeur et véhicule à la clôture du trajet. */
    private Mono<Void> unlinkDriverVehicleForTrip(
        UUID driverId,
        UUID vehicleId
    ) {
        Mono<Void> clearDriver = driverPersistence
            .findById(driverId)
            .flatMap(driver ->
                vehicleId.equals(driver.assignedVehicleId())
                    ? driverPersistence.updateVehicleAssignment(driverId, null)
                    : Mono.empty()
            );

        Mono<Void> clearVehicle = vehicleRepository
            .findById(vehicleId)
            .flatMap(v -> {
                if (driverId.equals(v.getCurrentDriverId())) {
                    v.setCurrentDriverId(null);
                    v.setNew(false);
                    return vehicleRepository.save(v);
                }
                return Mono.empty();
            })
            .then();

        return clearDriver.then(clearVehicle);
    }

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
        e.setDepartureLat(cmd.departureLat());
        e.setDepartureLng(cmd.departureLng());
        e.setDepartureKmIndex(cmd.departureKmIndex());
        e.setDepartureFuelIndex(cmd.departureFuelIndex());
        e.setMissionObject(cmd.missionObject());
        e.setMissionCost(cmd.missionCost());
        e.setMissionCostCurrency(
            cmd.missionCostCurrency() != null ? cmd.missionCostCurrency() : "XAF"
        );
        e.setDepartureRegisteredAt(null);
        e.setRateType(cmd.rateType());
        e.setScheduledReturnDatetime(cmd.scheduledReturnDatetime());
        e.setNew(true);
        return e;
    }

    private Mono<Void> saveDetails(UUID tripId, List<TripDetailInput> inputs) {
        if (inputs == null || inputs.isEmpty()) return Mono.empty();
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
                        : d.quantity()
                );
                de.setNew(true);
                return de;
            })
            .collect(Collectors.toList());
        return detailRepository.saveAll(entities).then();
    }

    private Mono<Trip> loadTripWithDetails(UUID tripId) {
        return tripRepository.findById(tripId).flatMap(entity ->
            detailRepository
                .findAllByTripIdOrderBySortOrder(entity.getId())
                .collectList()
                .map(details -> mapToDomain(entity, details))
        );
    }

    private Trip mapToDomain(
        TripEntity e,
        List<TripDetailEntity> detailEntities
    ) {
        List<TripDetail> details = detailEntities
            .stream()
            .map(d ->
                new TripDetail(
                    d.getId(),
                    d.getTripId(),
                    TripDetail.ItemType.valueOf(d.getItemType()),
                    d.getDescription(),
                    d.getQuantity(),
                    d.getWeight(),
                    d.getDepartureQuantity(),
                    d.getReturnQuantity(),
                    d.getSortOrder()
                )
            )
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
            e.getDepartureLat(),
            e.getDepartureLng(),
            e.getDepartureKmIndex(),
            e.getDepartureFuelIndex(),
            e.getEndDate(),
            e.getEndTime(),
            e.getReturnLocation(),
            e.getReturnLat(),
            e.getReturnLng(),
            e.getReturnKmIndex(),
            e.getReturnFuelIndex(),
            e.getReturnRegisteredAt(),
            e.getScheduledReturnDatetime(),
            e.getMissionObject(),
            e.getMissionCost(),
            e.getMissionCostCurrency(),
            rateType,
            e.getDepartureRegisteredAt(),
            e.getDistanceKm(),
            e.getDurationMinutes(),
            e.getComputedDistanceKm(),
            e.getComputedFuelConsumed(),
            e.getCancelReason(),
            e.getCancelledAt(),
            details
        );
    }
}
