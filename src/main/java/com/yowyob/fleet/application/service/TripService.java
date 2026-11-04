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
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FuelRechargeR2dbcRepository;
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
    private final AccessScopeService accessScope;
    private final ResourceAvailabilityService availability;
    private final FuelRechargeR2dbcRepository fuelRechargeRepository;
    private final TripStopService stopService;

    // ── Création d'un trajet (Manager) ────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Trip> createTrip(CreateTripCommand cmd) {
        LocalDateTime slotStart = ResourceAvailabilityService.slotStart(cmd.startDate(), cmd.startTime());
        LocalDateTime slotEnd = ResourceAvailabilityService.slotEnd(cmd.scheduledReturnDatetime());
        if (!slotEnd.isAfter(slotStart)) {
            return Mono.error(TripException.invalidSchedule());
        }
        // Tournée : les arrêts saisis, ou un arrêt unique à la destination (ancien formulaire).
        List<com.yowyob.fleet.domain.model.TripStopRules.StopInput> plan = cmd.stops() != null && !cmd.stops().isEmpty()
            ? cmd.stops()
            : (cmd.destinationLocation() != null && !cmd.destinationLocation().isBlank()
                ? List.of(new com.yowyob.fleet.domain.model.TripStopRules.StopInput("DELIVERY", cmd.destinationLocation(),
                    cmd.destinationLat(), cmd.destinationLng(), null, null, null, null))
                : List.of());
        try {
            com.yowyob.fleet.domain.model.TripStopRules.validatePlan(plan,
                slotStart, cmd.scheduledReturnDatetime());
        } catch (TripException e) {
            return Mono.error(e);
        }
        var lastStop = plan.get(plan.size() - 1);
        return Mono.zip(
            vehicleRepository
                .findById(cmd.vehicleId())
                .filter(v -> v.getDeletedAt() == null)
                .switchIfEmpty(
                    Mono.error(TripException.notFound(cmd.vehicleId()))
                ),
            // Sans ce switchIfEmpty, un conducteur inexistant vidait le zip :
            // la requête répondait 201 sans corps et sans rien créer.
            driverPersistence.findById(cmd.driverId())
                .switchIfEmpty(
                    Mono.error(TripException.notFound(cmd.driverId()))
                )
        ).flatMap(tuple -> {
            var vehicle = tuple.getT1();
            var driver = tuple.getT2();
            // Disponibilité par créneau : un véhicule actuellement en trajet peut être
            // planifié sur un créneau ultérieur ; seul un véhicule en maintenance est exclu.
            if (!com.yowyob.fleet.domain.model.VehicleLifecycle.isDispatchable(vehicle.getStatus())) {
                return Mono.error(TripException.vehicleUnavailable(vehicle.getStatus()));
            }

            UUID fleetId = vehicle.getFleetId();
            if (fleetId == null) {
                return Mono.error(new IllegalArgumentException("fleetId introuvable pour ce véhicule"));
            }
            // Le trajet appartient à la flotte de son véhicule : une flotte différente
            // permettait d'utiliser le véhicule d'une autre flotte (voire d'un autre gestionnaire).
            if (cmd.fleetId() != null && !cmd.fleetId().equals(fleetId)) {
                return Mono.error(TripException.vehicleNotInFleet());
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
                    lastStop.location().trim(),
                    lastStop.latitude(),
                    lastStop.longitude(),
                    cmd.scheduledReturnDatetime(),
                    cmd.details(),
                    plan
            );

            // Le véhicule n'est plus réservé (ON_TRIP) dès la planification : l'exclusivité
            // est garantie par créneau horaire (trajets ET affectations), ce qui permet de
            // planifier plusieurs trajets successifs pour le même véhicule. Il passe ON_TRIP
            // au départ réel (startTrip).
            return accessScope.assertTripFleetAccess(cmd.managerId(), fleetId)
                .then(availability.assertFleetOperable(fleetId))
                .then(availability.assertDriverEngageable(cmd.managerId(), driver, TripException::driverInactive))
                .then(availability.assertCrewQualified(cmd.driverId(), cmd.vehicleId(), slotStart, slotEnd))
                .then(availability.assertTripSlotFree(cmd.vehicleId(), cmd.driverId(), slotStart, slotEnd, null))
                .then(Mono.defer(() -> saveNewTrip(resolvedCmd)));
        });
    }

    private Mono<Trip> saveNewTrip(CreateTripCommand cmd) {
        // NB: pas de retry ici. `createTrip` est @Transactional : un doublon sur
        // l'INSERT (trip_code déjà utilisé) fait échouer/abandonner la transaction
        // PostgreSQL en cours (« current transaction is aborted »). Toute nouvelle
        // requête SQL sur CETTE MÊME transaction échoue alors immédiatement, y
        // compris un nouvel appel à generate_trip_code() — un retry silencieux ici
        // ne fait que masquer l'échec réel derrière une erreur 500 incompréhensible.
        // On surface donc une erreur métier claire ; le client peut relancer
        // l'action (une nouvelle requête HTTP ouvre une nouvelle transaction).
        return generateTripCode()
                .flatMap(tripCode -> persistTrip(tripCode, cmd))
                .onErrorResume(DuplicateKeyException.class, ex -> {
                    log.warn("⚠️ [TRIP] Collision de trip_code : vérifier la synchronisation de "
                            + "fleet.trip_code_seq (migration 040) : {}", ex.getMessage());
                    return Mono.error(TripException.codeCollision());
                });
    }

    private Mono<String> generateTripCode() {
        return db.sql("SELECT fleet.generate_trip_code() AS code")
                .map(row -> row.get("code", String.class))
                .one();
    }

    private Mono<Trip> persistTrip(String tripCode, CreateTripCommand cmd) {
        TripEntity entity = buildTripEntity(tripCode, cmd);
        return relinkAssignment(entity)
                .flatMap(tripRepository::save)
                .flatMap(saved -> stopService.persistPlan(saved, cmd.stops(), cmd.details())
                        .then(loadTripWithDetails(saved.getId())));
    }

    // ── Lancement effectif d'un trajet planifié ───────────────────────────────

    @Override
    @Transactional
    public Mono<Trip> startTrip(UUID tripId, StartTripCommand cmd, UUID managerId) {
        return tripRepository
            .findById(tripId)
            .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
            .flatMap(trip -> accessScope.assertTripFleetAccess(managerId, trip.getFleetId()).thenReturn(trip))
            .flatMap(trip -> {
                if (!"SCHEDULED".equals(trip.getStatus())) {
                    return Mono.error(TripException.invalidStartState());
                }

                LocalDateTime now = LocalDateTime.now().withNano(0);
                LocalDateTime plannedEnd = ResourceAvailabilityService.slotEnd(trip.getScheduledReturnDatetime());
                // Créneau réel à partir du départ effectif : un départ anticipé peut
                // empiéter sur un autre trajet ou une affectation planifiés entre-temps.
                LocalDateTime actualEnd = plannedEnd.isAfter(now) ? plannedEnd : now.plusMinutes(1);

                return vehicleRepository
                    .findById(trip.getVehicleId())
                    .filter(v -> v.getDeletedAt() == null)
                    .switchIfEmpty(
                        Mono.error(TripException.notFound(trip.getVehicleId()))
                    )
                    .flatMap(vehicle -> {
                        if (!com.yowyob.fleet.domain.model.VehicleLifecycle.isDispatchable(vehicle.getStatus())) {
                            return Mono.error(TripException.vehicleUnavailable(vehicle.getStatus()));
                        }
                        // Relevés au départ réel : saisis au départ, sinon ceux de la planification.
                        BigDecimal requestedKm = cmd != null ? cmd.departureKmIndex() : null;
                        BigDecimal fuelIndex = cmd != null && cmd.departureFuelIndex() != null
                            ? cmd.departureFuelIndex() : trip.getDepartureFuelIndex();
                        if (fuelIndex != null && vehicle.getTankCapacity() != null
                                && fuelIndex.compareTo(vehicle.getTankCapacity()) > 0) {
                            return Mono.error(TripException.departureFuelExceedsTank(vehicle.getTankCapacity()));
                        }
                        return currentOdometer(trip.getVehicleId()).flatMap(odometer -> {
                        // Le compteur ne recule pas : un index saisi sous le dernier relevé est une erreur ;
                        // un index planifié devenu inférieur (autres trajets depuis) est remplacé par le relevé.
                        if (requestedKm != null && requestedKm.compareTo(odometer) < 0) {
                            return Mono.<Trip>error(TripException.departureKmBelowOdometer(odometer));
                        }
                        BigDecimal plannedKm = trip.getDepartureKmIndex();
                        BigDecimal kmIndex = requestedKm != null ? requestedKm
                            : (plannedKm != null && plannedKm.compareTo(odometer) >= 0 ? plannedKm
                               : (odometer.signum() > 0 ? odometer : plannedKm));
                        return availability.assertFleetOperable(trip.getFleetId())
                            .then(driverPersistence.findById(trip.getDriverId()))
                            .switchIfEmpty(Mono.error(TripException.notFound(trip.getDriverId())))
                            .flatMap(driver -> availability.assertDriverEngageable(
                                managerId, driver, TripException::driverInactive))
                            // Le permis peut avoir expiré depuis la planification.
                            .then(availability.assertCrewQualified(
                                trip.getDriverId(), trip.getVehicleId(), now, actualEnd))
                            .then(tripRepository.existsInProgressTripForVehicle(trip.getVehicleId(), tripId))
                            .flatMap(busy -> Boolean.TRUE.equals(busy)
                                ? Mono.<Void>error(TripException.vehicleOccupied())
                                : Mono.<Void>empty())
                            .then(tripRepository.existsInProgressTripForDriver(trip.getDriverId(), tripId))
                            .flatMap(busy -> Boolean.TRUE.equals(busy)
                                ? Mono.<Void>error(TripException.driverOccupied())
                                : Mono.<Void>empty())
                            .then(availability.assertTripSlotFree(
                                trip.getVehicleId(), trip.getDriverId(), now, actualEnd, tripId))
                            .then(Mono.defer(() -> {
                                trip.setStatus("DEPARTED");
                                trip.setDepartureKmIndex(kmIndex);
                                trip.setDepartureFuelIndex(fuelIndex);
                                trip.setStartDate(now.toLocalDate());
                                trip.setStartTime(now.toLocalTime());
                                trip.setDepartureRegisteredAt(Instant.now());
                                trip.setNew(false);

                                vehicle.setStatus("ON_TRIP");
                                vehicle.setNew(false);

                                return vehicleRepository
                                    .save(vehicle)
                                    .then(linkDriverVehicleIfFree(trip.getDriverId(), trip.getVehicleId()))
                                    .flatMap(linked -> {
                                        trip.setDriverVehicleLinked(linked);
                                        return tripRepository.save(trip);
                                    })
                                    // Le premier départ ouvre le poste qui couvre le trajet.
                                    .flatMap(saved -> startShift(saved.getAssignmentId()).thenReturn(saved))
                                    .flatMap(saved -> loadTripWithDetails(saved.getId()));
                            }));
                        });
                    });
            });
    }

    private Mono<BigDecimal> currentOdometer(UUID vehicleId) {
        return operationalRepo.findByVehicleId(vehicleId)
            .map(op -> op.getOdometerReading() != null ? op.getOdometerReading() : BigDecimal.ZERO)
            .defaultIfEmpty(BigDecimal.ZERO);
    }

    private Mono<Void> startShift(UUID assignmentId) {
        if (assignmentId == null) return Mono.empty();
        return db.sql("UPDATE fleet.assignments SET status = 'IN_PROGRESS' WHERE id = :id AND status = 'PENDING'")
            .bind("id", assignmentId)
            .fetch().rowsUpdated().then();
    }

    // ── Arrivée à destination (livraison) ─────────────────────────────────────

    @Override
    @Transactional
    public Mono<Trip> registerArrival(UUID tripId, ArrivalCommand cmd, UUID actorId) {
        return tripRepository
            .findById(tripId)
            .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
            .flatMap(trip -> assertDriverOrFleetAccess(actorId, trip).thenReturn(trip))
            .flatMap(trip -> {
                if (!"DEPARTED".equals(trip.getStatus())) {
                    return Mono.error(TripException.invalidArrivalState());
                }
                List<DeliveredDetailInput> delivered = cmd.delivered() != null ? cmd.delivered() : List.of();
                Mono<Void> deliveries = Flux.fromIterable(delivered)
                    .concatMap(d -> detailRepository.findById(d.detailId())
                        .filter(de -> tripId.equals(de.getTripId()))
                        .switchIfEmpty(Mono.error(TripException.detailNotInTrip()))
                        .flatMap(de -> {
                            int loaded = de.getDepartureQuantity() != null ? de.getDepartureQuantity() : de.getQuantity();
                            Integer qty = d.deliveredQuantity();
                            if (qty == null || qty < 0 || qty > loaded) {
                                return Mono.error(TripException.invalidDeliveredQuantity(loaded));
                            }
                            de.setDeliveredQuantity(qty);
                            de.setNew(false);
                            return detailRepository.save(de);
                        }))
                    .then();
                trip.setStatus("ARRIVED");
                // Saisie hors ligne : l'heure sur place est gardée, bornée entre le départ et maintenant.
                LocalDateTime now = LocalDateTime.now().withNano(0);
                LocalDateTime arrivedAt = cmd.arrivedAt() != null && cmd.arrivedAt().isBefore(now) ? cmd.arrivedAt() : now;
                LocalDateTime departedAt = trip.getStartDate() != null
                    ? ResourceAvailabilityService.slotStart(trip.getStartDate(), trip.getStartTime()) : null;
                trip.setArrivedAt(departedAt != null && arrivedAt.isBefore(departedAt) ? departedAt : arrivedAt);
                trip.setArrivalLocation(cmd.arrivalLocation() != null && !cmd.arrivalLocation().isBlank()
                    ? cmd.arrivalLocation() : trip.getDestinationLocation());
                trip.setArrivalLat(cmd.arrivalLat());
                trip.setArrivalLng(cmd.arrivalLng());
                trip.setArrivalNotes(cmd.notes());
                trip.setNew(false);
                return deliveries
                    .then(Mono.defer(() -> stopService.completeRemaining(tripId, trip.getArrivedAt(), cmd.notes())))
                    .then(Mono.defer(() -> tripRepository.save(trip)))
                    .flatMap(saved -> loadTripWithDetails(saved.getId()));
            });
    }

    /** Le chauffeur du trajet, ou un gestionnaire ayant accès à sa flotte. */
    private Mono<Void> assertDriverOrFleetAccess(UUID actorId, TripEntity trip) {
        return actorId.equals(trip.getDriverId())
            ? Mono.empty()
            : accessScope.assertTripFleetAccess(actorId, trip.getFleetId());
    }

    // ── Enregistrement du retour ──────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Trip> registerReturn(RegisterReturnCommand cmd, UUID actorId) {
        return tripRepository
            .findByTripCode(cmd.tripCode())
            .switchIfEmpty(
                Mono.error(TripException.notFoundByCode(cmd.tripCode()))
            )
            .flatMap(trip -> accessScope.assertTripFleetAccess(actorId, trip.getFleetId()).thenReturn(trip))
            .flatMap(trip -> {
                if (
                    "COMPLETED".equals(trip.getStatus()) ||
                    "CANCELLED".equals(trip.getStatus())
                ) {
                    return Mono.error(TripException.actionOnCompletedTrip());
                }
                // Cycle SCHEDULED → DEPARTED → (ARRIVED) → COMPLETED : sans départ
                // enregistré, ni l'heure de départ réelle ni le créneau ne sont connus.
                if (!isInProgress(trip.getStatus())) {
                    return Mono.error(TripException.notDeparted());
                }
                if (
                    trip.getStartDate() != null &&
                    LocalDateTime.of(cmd.returnDate(), cmd.returnTime()).isBefore(
                        ResourceAvailabilityService.slotStart(trip.getStartDate(), trip.getStartTime()))
                ) {
                    return Mono.error(TripException.returnBeforeDeparture());
                }
                // Même règle que le formulaire de retour du front : sans elle, un
                // replay offline ou un appel API direct ferait reculer l'odomètre.
                if (
                    cmd.returnKmIndex() != null &&
                    trip.getDepartureKmIndex() != null &&
                    cmd.returnKmIndex().compareTo(trip.getDepartureKmIndex()) < 0
                ) {
                    return Mono.error(TripException.invalidReturnKmIndex());
                }

                return refueledDuringTrip(trip).flatMap(refueled -> {
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
                    // Consommation = départ + pleins enregistrés pendant le trajet − retour
                    // (départ − retour seul devenait négatif dès qu'un plein était fait en route).
                    BigDecimal computedFuel;
                    try {
                        computedFuel = computeFuelConsumed(
                            trip.getDepartureFuelIndex(), refueled, cmd.returnFuelIndex());
                    } catch (TripException e) {
                        return Mono.error(e);
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
                    Mono<Void> vehicleFree = releaseVehicle(trip.getVehicleId(), trip.getId());

                    // Mise à jour odomètre : l'index km de retour EST le relevé du compteur
                    // (doc §7.6 « vehicle.currentKm = returnKmIndex »). Y ajouter la distance
                    // comptait deux fois les km déjà remontés (télémétrie, saisie chauffeur).
                    final BigDecimal returnKm = cmd.returnKmIndex();
                    Mono<Void> odometer = (returnKm != null)
                        ? operationalRepo
                              .findByVehicleId(trip.getVehicleId())
                              .flatMap(op -> {
                                  BigDecimal cur = op.getOdometerReading() != null
                                      ? op.getOdometerReading()
                                      : BigDecimal.ZERO;
                                  BigDecimal next = returnKm.max(cur);
                                  op.setOdometerReading(next);
                                  op.setMileage(next);
                                  return operationalRepo.save(op);
                              })
                              .then()
                        : Mono.empty();

                    // Mise à jour quantités retour dans les détails (de CE trajet uniquement)
                    Mono<Void> detailsUpdate = (cmd.detailUpdates() != null &&
                        !cmd.detailUpdates().isEmpty())
                        ? Flux.fromIterable(cmd.detailUpdates())
                              .flatMap(du ->
                                  detailRepository
                                      .findById(du.detailId())
                                      .filter(de -> trip.getId().equals(de.getTripId()))
                                      .flatMap(de -> {
                                          de.setReturnQuantity(du.returnQuantity());
                                          de.setNew(false);
                                          return detailRepository.save(de);
                                      })
                              )
                              .then()
                        : Mono.empty();

                    final TripEntity tripToSave = trip;
                    Mono<Void> unlinkPair = trip.isDriverVehicleLinked()
                        ? unlinkDriverVehicleForTrip(trip.getDriverId(), trip.getVehicleId())
                        : Mono.empty();
                    return Mono.when(vehicleFree, odometer, detailsUpdate, unlinkPair, stopService.closeRemaining(trip.getId()))
                        .then(tripRepository.save(tripToSave))
                        .flatMap(saved -> loadTripWithDetails(saved.getId()));
                });
            });
    }

    /**
     * Litres rechargés sur le véhicule depuis le départ réel du trajet jusqu'à maintenant :
     * le véhicule est engagé sur ce trajet tant que son retour n'est pas enregistré, et une
     * recharge est datée de sa saisie (qui peut suivre l'heure de retour déclarée).
     */
    private Mono<BigDecimal> refueledDuringTrip(TripEntity trip) {
        if (trip.getStartDate() == null || trip.getDepartureFuelIndex() == null) {
            return Mono.just(BigDecimal.ZERO);
        }
        LocalDateTime departure = ResourceAvailabilityService.slotStart(trip.getStartDate(), trip.getStartTime());
        return fuelRechargeRepository
            .sumQuantityByVehicleAndPeriod(trip.getVehicleId(), departure, LocalDateTime.now().plusSeconds(1))
            .defaultIfEmpty(BigDecimal.ZERO);
    }

    /**
     * Carburant consommé (L) = départ + rechargé pendant le trajet − retour ; null si un index
     * manque. Un résultat négatif signifie qu'un plein n'a pas été enregistré (ou un index
     * erroné) : le retour est refusé plutôt que d'enregistrer une consommation impossible.
     */
    static BigDecimal computeFuelConsumed(BigDecimal departure, BigDecimal refueled, BigDecimal ret) {
        if (departure == null || ret == null) return null;
        BigDecimal available = departure.add(refueled != null ? refueled : BigDecimal.ZERO);
        if (ret.compareTo(available) > 0) {
            throw TripException.returnFuelExceedsAvailable(available);
        }
        return available.subtract(ret);
    }

    // ── Recherche par code ────────────────────────────────────────────────────

    @Override
    public Mono<Trip> getTripByCode(String tripCode, UUID actorId) {
        return tripRepository
            .findByTripCode(tripCode)
            .switchIfEmpty(Mono.error(TripException.notFoundByCode(tripCode)))
            .flatMap(e -> accessScope.assertTripFleetAccess(actorId, e.getFleetId())
                    .then(loadTripWithDetails(e.getId())));
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
            .flatMap(trip -> accessScope.assertTripFleetAccess(managerId, trip.getFleetId()).thenReturn(trip))
            .flatMap(trip -> {
                if (!isModifiable(trip.getStatus())) {
                    return Mono.error(TripException.tripNotModifiable());
                }
                return changeResources(trip, managerId, trip.getVehicleId(), newDriverId,
                        trip.getStartDate(), trip.getStartTime())
                    .flatMap(this::relinkAssignment)
                    .flatMap(tripRepository::save)
                    .flatMap(saved -> loadTripWithDetails(saved.getId()));
            });
    }

    // ── Annulation ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Trip> cancelTrip(UUID tripId, String reason, UUID managerId) {
        return tripRepository
            .findById(tripId)
            .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
            .flatMap(trip -> accessScope.assertTripFleetAccess(managerId, trip.getFleetId()).thenReturn(trip))
            .flatMap(trip -> {
                if (
                    "COMPLETED".equals(trip.getStatus()) ||
                    "CANCELLED".equals(trip.getStatus()) ||
                    "MISSED".equals(trip.getStatus())
                ) {
                    return Mono.error(TripException.actionOnCompletedTrip());
                }

                // Seul un trajet parti (aller OU retour) mobilise physiquement le véhicule.
                boolean wasInProgress = isInProgress(trip.getStatus());
                boolean wasLinked = trip.isDriverVehicleLinked();
                trip.setStatus("CANCELLED");
                trip.setCancelReason(reason);
                trip.setCancelledAt(Instant.now());
                trip.setDriverVehicleLinked(false);
                trip.setNew(false);

                Mono<Void> freeVehicle = wasInProgress
                    ? releaseVehicle(trip.getVehicleId(), trip.getId())
                    : Mono.empty();

                Mono<Void> unlinkPair = wasLinked
                    ? unlinkDriverVehicleForTrip(trip.getDriverId(), trip.getVehicleId())
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
        Flux<TripEntity> source;
        if (fleetId != null) {
            source = accessScope.assertTripFleetAccess(managerId, fleetId)
                    .thenMany(tripRepository.findAllByFleetId(fleetId));
        } else {
            source = tripRepository.findAllByFleetManagerId(managerId);
        }
        return source.flatMap(e -> loadTripWithDetails(e.getId()));
    }

    @Override
    @Transactional
    public Mono<Void> deleteTrip(UUID tripId, UUID managerId) {
        return tripRepository
            .findById(tripId)
            .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
            .flatMap(trip -> accessScope.assertTripFleetAccess(managerId, trip.getFleetId()).then(Mono.defer(() -> {
                String status = trip.getStatus();
                if ("SCHEDULED".equals(status) || "CANCELLED".equals(status)) {
                    return tripRepository.deleteById(tripId);
                }
                return Mono.error(new IllegalStateException(
                        "Seuls les trajets SCHEDULED ou CANCELLED peuvent être supprimés. Annulez-le d'abord."));
            })));
    }

    @Override
    public Mono<Trip> getTripById(UUID id) {
        return tripRepository
            .findById(id)
            .switchIfEmpty(Mono.error(TripException.notFound(id)))
            .flatMap(e -> loadTripWithDetails(e.getId()));
    }

    @Override
    public Mono<Trip> getTripById(UUID id, UUID actorId) {
        return tripRepository
            .findById(id)
            .switchIfEmpty(Mono.error(TripException.notFound(id)))
            .flatMap(e -> accessScope.assertTripFleetAccess(actorId, e.getFleetId())
                    .then(loadTripWithDetails(e.getId())));
    }

    @Override
    public Flux<Trip> getOpenTrips(UUID managerId) {
        return tripRepository
            .findAllByFleetManagerId(managerId)
            // « Départ enregistré, retour en attente » (contrat de /trips/open) : seuls ces
            // trajets peuvent recevoir un retour ; la version hors ligne filtrait déjà ainsi.
            .filter(t -> isInProgress(t.getStatus()))
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
            .flatMap(trip -> accessScope.assertTripFleetAccess(managerId, trip.getFleetId()).thenReturn(trip))
            .flatMap(trip -> {
                if (!isModifiable(trip.getStatus())) {
                    return Mono.error(TripException.tripNotModifiable());
                }
                return changeResources(trip, managerId, newVehicleId, trip.getDriverId(),
                        trip.getStartDate(), trip.getStartTime())
                    .flatMap(this::relinkAssignment)
                    .flatMap(tripRepository::save)
                    .flatMap(saved -> loadTripWithDetails(saved.getId()));
            });
    }

    @Override
    @Transactional
    public Mono<Trip> updateTrip(UUID tripId, UpdateTripCommand cmd) {
        return tripRepository
            .findById(tripId)
            .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
            .flatMap(trip -> accessScope.assertTripFleetAccess(cmd.managerId(), trip.getFleetId()).thenReturn(trip))
            .flatMap(trip -> {
                if (!isModifiable(trip.getStatus())) {
                    return Mono.error(TripException.tripNotModifiable());
                }
                // Véhicule, chauffeur et horaire de départ sont validés ensemble (créneau)
                // puis appliqués sur CETTE instance, enregistrée une seule fois. L'ancien code
                // changeait le véhicule via updateTripVehicle puis réenregistrait une copie
                // périmée du trajet, ce qui annulait le changement de véhicule.
                return changeResources(
                        trip,
                        cmd.managerId(),
                        cmd.vehicleId() != null ? cmd.vehicleId() : trip.getVehicleId(),
                        cmd.driverId() != null ? cmd.driverId() : trip.getDriverId(),
                        cmd.startDate() != null ? cmd.startDate() : trip.getStartDate(),
                        cmd.startTime() != null ? cmd.startTime() : trip.getStartTime())
                    .flatMap(updated -> {
                        if (cmd.departureLocation() != null) updated.setDepartureLocation(
                            cmd.departureLocation()
                        );
                        if (cmd.departureLat() != null) updated.setDepartureLat(
                            cmd.departureLat()
                        );
                        if (cmd.departureLng() != null) updated.setDepartureLng(
                            cmd.departureLng()
                        );
                        if (cmd.departureKmIndex() != null) updated.setDepartureKmIndex(
                            cmd.departureKmIndex()
                        );
                        if (cmd.departureFuelIndex() != null) updated.setDepartureFuelIndex(
                            cmd.departureFuelIndex()
                        );
                        if (cmd.missionObject() != null) updated.setMissionObject(
                            cmd.missionObject()
                        );
                        Mono<Void> moveStop = Mono.empty();
                        if (cmd.destinationLocation() != null && !cmd.destinationLocation().isBlank()) {
                            // La destination est le dernier arrêt : seul un trajet à arrêt unique la déplace ici.
                            moveStop = stopService.moveSingleStop(updated.getId(), cmd.destinationLocation(),
                                cmd.destinationLat(), cmd.destinationLng());
                        }
                        updated.setNew(false);
                        Mono<Void> stopMove = moveStop;
                        return relinkAssignment(updated).flatMap(tripRepository::save)
                            .flatMap(saved -> stopMove.then(Mono.defer(() -> tripRepository.findById(saved.getId()))));
                    })
                    .flatMap(saved -> loadTripWithDetails(saved.getId()));
            });
    }

    /**
     * Valide puis applique (sans enregistrer) un changement de véhicule, de chauffeur
     * et/ou d'horaire de départ d'un trajet non clôturé, avec les mêmes règles qu'à la
     * création : ressources engageables et créneau libre (trajets + affectations).
     * Pour un trajet en cours, bascule aussi le statut des véhicules et la liaison.
     */
    private Mono<TripEntity> changeResources(TripEntity trip, UUID actorId,
                                             UUID nextVehicleId, UUID nextDriverId,
                                             LocalDate nextStartDate, LocalTime nextStartTime) {
        boolean vehicleChanged = !nextVehicleId.equals(trip.getVehicleId());
        boolean driverChanged = !nextDriverId.equals(trip.getDriverId());
        boolean slotChanged = !java.util.Objects.equals(nextStartDate, trip.getStartDate())
                || !java.util.Objects.equals(nextStartTime, trip.getStartTime());
        if (!vehicleChanged && !driverChanged && !slotChanged) {
            return Mono.just(trip);
        }
        boolean inProgress = isInProgress(trip.getStatus());
        LocalDateTime start = ResourceAvailabilityService.slotStart(nextStartDate, nextStartTime);
        LocalDateTime end = ResourceAvailabilityService.slotEnd(trip.getScheduledReturnDatetime());
        if (!end.isAfter(start)) {
            return Mono.error(TripException.invalidSchedule());
        }

        Mono<Void> driverCheck = !driverChanged
            ? Mono.empty()
            : driverPersistence.findById(nextDriverId)
                .switchIfEmpty(Mono.error(TripException.notFound(nextDriverId)))
                .flatMap(driver -> availability.assertDriverEngageable(actorId, driver, TripException::driverInactive))
                .then(inProgress
                    ? tripRepository.existsInProgressTripForDriver(nextDriverId, trip.getId())
                        .flatMap(busy -> Boolean.TRUE.equals(busy)
                            ? Mono.<Void>error(TripException.driverOccupied())
                            : Mono.<Void>empty())
                    : Mono.<Void>empty());

        Mono<Void> vehicleCheck = !vehicleChanged
            ? Mono.empty()
            : vehicleRepository.findById(nextVehicleId)
                .filter(v -> v.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(TripException.notFound(nextVehicleId)))
                .flatMap(vehicle -> {
                    if (!java.util.Objects.equals(trip.getFleetId(), vehicle.getFleetId())) {
                        return Mono.<Void>error(TripException.vehicleNotInFleet());
                    }
                    if (!com.yowyob.fleet.domain.model.VehicleLifecycle.isDispatchable(vehicle.getStatus())) {
                        return Mono.<Void>error(TripException.vehicleUnavailable(vehicle.getStatus()));
                    }
                    return inProgress
                        ? tripRepository.existsInProgressTripForVehicle(nextVehicleId, trip.getId())
                            .flatMap(busy -> Boolean.TRUE.equals(busy)
                                ? Mono.<Void>error(TripException.vehicleOccupied())
                                : Mono.<Void>empty())
                        : Mono.<Void>empty();
                });

        return driverCheck
            .then(vehicleCheck)
            .then(availability.assertCrewQualified(nextDriverId, nextVehicleId, start, end))
            .then(availability.assertTripSlotFree(nextVehicleId, nextDriverId, start, end, trip.getId()))
            .then(inProgress
                ? switchInProgressResources(trip, nextVehicleId, nextDriverId)
                : Mono.<Void>empty())
            .then(Mono.fromCallable(() -> {
                // Véhicule de remplacement affecté à un trajet interrompu : la mission reprend.
                if (inProgress && vehicleChanged && trip.isInterrupted()) {
                    trip.setResumedAt(LocalDateTime.now());
                }
                trip.setVehicleId(nextVehicleId);
                trip.setDriverId(nextDriverId);
                trip.setStartDate(nextStartDate);
                trip.setStartTime(nextStartTime);
                trip.setNew(false);
                return trip;
            }));
    }

    /** Trajet en cours : le nouveau véhicule part, l'ancien est libéré, la liaison suit. */
    private Mono<Void> switchInProgressResources(TripEntity trip, UUID nextVehicleId, UUID nextDriverId) {
        boolean vehicleChanged = !nextVehicleId.equals(trip.getVehicleId());
        Mono<Void> unlinkOld = trip.isDriverVehicleLinked()
            ? unlinkDriverVehicleForTrip(trip.getDriverId(), trip.getVehicleId())
            : Mono.empty();
        Mono<Void> vehicles = vehicleChanged
            ? releaseVehicle(trip.getVehicleId(), trip.getId())
                .then(vehicleRepository.findById(nextVehicleId)
                    .flatMap(v -> {
                        v.setStatus("ON_TRIP");
                        v.setNew(false);
                        return vehicleRepository.save(v);
                    })
                    .then())
            : Mono.empty();
        return unlinkOld
            .then(vehicles)
            .then(linkDriverVehicleIfFree(nextDriverId, nextVehicleId))
            .doOnNext(trip::setDriverVehicleLinked)
            .then();
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
                // Un complément décrit un changement de chargement constaté en route.
                if (!isInProgress(trip.getStatus())) {
                    return Mono.error(TripException.notDeparted());
                }
                String changeType = "REMOVE".equalsIgnoreCase(input.changeType()) ? "REMOVE" : "ADD";
                Mono<Void> stopCheck = input.stopId() == null || "REMOVE".equals(changeType)
                    ? Mono.empty()
                    : stopService.stopForNewLine(tripId, input.stopId())
                        .filter(input.stopId()::equals)
                        .switchIfEmpty(Mono.error(TripException.stopNotFound()))
                        .then();
                Mono<Void> detailCheck = "REMOVE".equals(changeType)
                    ? (input.detailId() == null
                        ? Mono.error(TripException.detailNotInTrip())
                        : detailRepository.findById(input.detailId())
                            .filter(de -> tripId.equals(de.getTripId()))
                            .switchIfEmpty(Mono.error(TripException.detailNotInTrip()))
                            .then())
                    : Mono.empty();
                TripMissionSubmissionEntity sub = new TripMissionSubmissionEntity();
                sub.setId(UUID.randomUUID());
                sub.setTripId(tripId);
                sub.setSubmittedBy(driverId);
                sub.setChangeType(changeType);
                sub.setDetailId("REMOVE".equals(changeType) ? input.detailId() : null);
                sub.setStopId("REMOVE".equals(changeType) ? null : input.stopId());
                sub.setItemType(input.itemType());
                sub.setDescription(input.description());
                sub.setQuantity(input.quantity());
                sub.setWeight(input.weight());
                sub.setNotes(input.notes());
                sub.setStatus("PENDING");
                sub.setCreatedAt(Instant.now());
                sub.setNew(true);
                return stopCheck.then(detailCheck).then(submissionRepository.save(sub)).map(
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
                    TripException.submissionNotPending()
                )
            )
            .flatMap(sub ->
                tripRepository
                    .findById(sub.getTripId())
                    .flatMap(trip -> accessScope.assertTripFleetAccess(managerId, trip.getFleetId()).thenReturn(trip))
                    .flatMap(trip -> {
                        sub.setStatus("APPROVED");
                        sub.setReviewedBy(managerId);
                        sub.setReviewedAt(Instant.now());
                        sub.setNew(false);
                        if ("REMOVE".equals(sub.getChangeType())) {
                            // Retrait : la ligne du manifeste est réduite (la quantité au départ reste l'historique).
                            return detailRepository.findById(sub.getDetailId())
                                .filter(de -> trip.getId().equals(de.getTripId()))
                                .switchIfEmpty(Mono.error(TripException.detailNotInTrip()))
                                .flatMap(de -> {
                                    int removed = sub.getQuantity() != null ? sub.getQuantity() : de.getQuantity();
                                    de.setQuantity(Math.max(0, de.getQuantity() - removed));
                                    de.setNew(false);
                                    return detailRepository.save(de);
                                })
                                .then(submissionRepository.save(sub))
                                .then(loadTripWithDetails(trip.getId()));
                        }
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
                        return stopService.stopForNewLine(trip.getId(), sub.getStopId())
                            .doOnNext(detail::setStopId)
                            .then(Mono.defer(() -> detailRepository.save(detail)))
                            .then(submissionRepository.save(sub))
                            .then(loadTripWithDetails(trip.getId()));
                    })
            );
    }

    @Override
    @Transactional
    public Mono<Void> rejectMissionSubmission(
        UUID submissionId,
        String reason,
        UUID managerId
    ) {
        return submissionRepository
            .findByIdAndStatus(submissionId, "PENDING")
            .switchIfEmpty(
                Mono.error(
                    TripException.submissionNotPending()
                )
            )
            .flatMap(sub -> tripRepository
                .findById(sub.getTripId())
                .flatMap(trip -> accessScope.assertTripFleetAccess(managerId, trip.getFleetId()))
                .then(Mono.defer(() -> {
                    sub.setStatus("REJECTED");
                    sub.setRejectionReason(reason);
                    sub.setReviewedBy(managerId);
                    sub.setReviewedAt(Instant.now());
                    sub.setNew(false);
                    return submissionRepository.save(sub).then();
                })));
    }

    @Override
    public Flux<TripMissionSubmissionEntity> listSubmissions(UUID tripId, UUID actorId) {
        return tripRepository
            .findById(tripId)
            .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
            .flatMap(trip -> assertDriverOrFleetAccess(actorId, trip))
            .thenMany(Flux.defer(() -> submissionRepository.findAllByTripIdOrderByCreatedAtDesc(tripId)));
    }

    // ── Télémétrie (conservé) ─────────────────────────────────────────────────

    @Override
    public Mono<Void> sendTelemetry(
        UUID tripId,
        UUID driverId,
        Double lat,
        Double lng,
        Double speed
    ) {
        return tripRepository
            .findById(tripId)
            // Un chauffeur ne peut alimenter que la position de SON trajet.
            .flatMap(t -> driverId.equals(t.getDriverId())
                ? Mono.just(t)
                : Mono.<TripEntity>error(TripException.forbidden()))
            .filter(
                t ->
                    "DEPARTED".equals(t.getStatus()) ||
                    "ARRIVED".equals(t.getStatus())
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
            // Parti (DEPARTED) ou arrivé à destination (ARRIVED) : les deux sont « en cours ».
            .findInProgressTripForDriver(driverId)
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
            "ARRIVED".equals(status) ||
            "SCHEDULED".equals(status)
        );
    }

    /** Départ enregistré, retour non enregistré : le trajet mobilise physiquement ses ressources. */
    private static boolean isInProgress(String status) {
        return "DEPARTED".equals(status) || "ARRIVED".equals(status);
    }

    /**
     * Repasse le véhicule AVAILABLE s'il était ON_TRIP et qu'aucun AUTRE trajet en cours
     * ne le mobilise. Ne touche jamais à un statut MAINTENANCE posé entre-temps.
     */
    private Mono<Void> releaseVehicle(UUID vehicleId, UUID closingTripId) {
        return tripRepository.existsInProgressTripForVehicle(vehicleId, closingTripId)
            .filter(busy -> !Boolean.TRUE.equals(busy))
            .flatMap(free -> vehicleRepository.findById(vehicleId))
            .filter(v -> "ON_TRIP".equals(v.getStatus()))
            .flatMap(v -> {
                v.setStatus("AVAILABLE");
                v.setNew(false);
                return vehicleRepository.save(v);
            })
            .then();
    }

    /**
     * Associe chauffeur et véhicule le temps d'un trajet, seulement si aucun des deux
     * n'a d'affectation permanente (ou s'ils sont déjà associés l'un à l'autre). L'ancien
     * code effaçait les affectations permanentes du chauffeur ET du véhicule au départ,
     * puis vidait tout au retour : chaque trajet détruisait les affectations existantes.
     *
     * @return true si c'est ce trajet qui a posé la liaison (à défaire à sa clôture)
     */
    private Mono<Boolean> linkDriverVehicleIfFree(UUID driverId, UUID vehicleId) {
        return Mono.zip(
                driverPersistence.findById(driverId),
                vehicleRepository.findById(vehicleId),
                driverPersistence.findByAssignedVehicleId(vehicleId).hasElement())
            .flatMap(t -> {
                var driver = t.getT1();
                var vehicle = t.getT2();
                boolean free = driver.assignedVehicleId() == null
                    && vehicle.getCurrentDriverId() == null
                    && !Boolean.TRUE.equals(t.getT3());
                if (!free) {
                    return Mono.just(false);
                }
                vehicle.setCurrentDriverId(driverId);
                vehicle.setNew(false);
                return vehicleRepository.save(vehicle)
                    .then(driverPersistence.updateVehicleAssignment(driverId, vehicleId))
                    .thenReturn(true);
            })
            .defaultIfEmpty(false);
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
        e.setDestinationLocation(cmd.destinationLocation());
        e.setDestinationLat(cmd.destinationLat());
        e.setDestinationLng(cmd.destinationLng());
        e.setDepartureRegisteredAt(null);
        e.setScheduledReturnDatetime(cmd.scheduledReturnDatetime());
        e.setNew(true);
        return e;
    }

    /**
     * Rattache un trajet planifié au poste (affectation) du même binôme qui couvre son heure de
     * départ ; le détache s'il n'en a plus. Un trajet parti garde son poste.
     */
    private Mono<TripEntity> relinkAssignment(TripEntity trip) {
        if (!"SCHEDULED".equals(trip.getStatus()) || trip.getStartDate() == null) {
            return Mono.just(trip);
        }
        LocalDateTime at = ResourceAvailabilityService.slotStart(trip.getStartDate(), trip.getStartTime());
        return db.sql("""
                SELECT id FROM fleet.assignments
                WHERE vehicle_id = :vehicleId AND driver_id = :driverId
                  AND status IN ('PENDING', 'IN_PROGRESS')
                  AND start_datetime <= :at AND end_datetime > :at
                ORDER BY start_datetime LIMIT 1
                """)
            .bind("vehicleId", trip.getVehicleId())
            .bind("driverId", trip.getDriverId())
            .bind("at", at)
            .map(row -> row.get("id", UUID.class))
            .one()
            .map(java.util.Optional::of)
            .defaultIfEmpty(java.util.Optional.empty())
            .map(found -> {
                trip.setAssignmentId(found.orElse(null));
                return trip;
            });
    }

    private Mono<Trip> loadTripWithDetails(UUID tripId) {
        return tripRepository.findById(tripId).flatMap(entity ->
            Mono.zip(
                detailRepository.findAllByTripIdOrderBySortOrder(entity.getId()).collectList(),
                stopService.stopsOf(entity.getId())
            ).map(t -> {
                Trip trip = mapToDomain(entity, t.getT1());
                trip.setStops(t.getT2());
                return trip;
            })
        );
    }

    private Trip mapToDomain(
        TripEntity e,
        List<TripDetailEntity> detailEntities
    ) {
        List<TripDetail> details = detailEntities
            .stream()
            .map(d -> {
                TripDetail detail = new TripDetail(
                    d.getId(),
                    d.getTripId(),
                    TripDetail.ItemType.valueOf(d.getItemType()),
                    d.getDescription(),
                    d.getQuantity(),
                    d.getWeight(),
                    d.getDepartureQuantity(),
                    d.getReturnQuantity(),
                    d.getSortOrder()
                );
                detail.setDeliveredQuantity(d.getDeliveredQuantity());
                detail.setStopId(d.getStopId());
                detail.setShortageReason(d.getShortageReason());
                return detail;
            })
            .collect(Collectors.toList());

        Trip.Status status = null;
        if (e.getStatus() != null) {
            try {
                status = Trip.Status.valueOf(e.getStatus());
            } catch (Exception ex) {
                /* ignore */
            }
        }

        Trip trip = new Trip(
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
            e.getDepartureRegisteredAt(),
            e.getDistanceKm(),
            e.getDurationMinutes(),
            e.getComputedDistanceKm(),
            e.getComputedFuelConsumed(),
            e.getCancelReason(),
            e.getCancelledAt(),
            details
        );
        trip.setInterruption(e.getInterruptedAt(), e.getResumedAt(), e.getInterruptionIncidentId());
        trip.setDestination(e.getDestinationLocation(), e.getDestinationLat(), e.getDestinationLng());
        trip.setArrival(e.getArrivedAt(), e.getArrivalLocation(), e.getArrivalNotes());
        trip.setAssignmentId(e.getAssignmentId());
        return trip;
    }
}
