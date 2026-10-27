package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.PlanningException;
import com.yowyob.fleet.domain.exception.TripException;
import com.yowyob.fleet.domain.model.Assignment;
import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.ports.out.AssignmentPersistencePort;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.ResourceLockPort;
import com.yowyob.fleet.domain.exception.DriverException;
import com.yowyob.fleet.domain.model.DriverDocument;
import com.yowyob.fleet.domain.model.DrivingLicenseRules;
import com.yowyob.fleet.domain.ports.out.DocumentPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleTypeEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleTypeR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Disponibilité des véhicules et des chauffeurs par CRÉNEAU HORAIRE
 * (GESTION_TRAJETS_ALLERS_RETOURS.md §6.5).
 *
 * Deux créneaux sont en conflit s'ils se chevauchent (début1 &lt; fin2 ET fin1 &gt; début2)
 * pour le même véhicule ou le même chauffeur. Règle commune aux trajets et aux affectations :
 * <ul>
 *   <li>trajet ↔ trajet : même véhicule ou même chauffeur ;</li>
 *   <li>affectation ↔ affectation : même véhicule ou même chauffeur ;</li>
 *   <li>trajet ↔ affectation : véhicule affecté à un AUTRE chauffeur, ou chauffeur affecté
 *       à un AUTRE véhicule. Un trajet du même binôme s'inscrit dans le cadre de son
 *       affectation (§7.1 : « une affectation peut servir de cadre à plusieurs trajets »).</li>
 * </ul>
 * Les vérifications verrouillent d'abord les ressources ({@link ResourceLockPort}) : elles
 * doivent être appelées dans une transaction qui inclut l'écriture de l'engagement.
 */
@Service
@RequiredArgsConstructor
public class ResourceAvailabilityService {

    /** Fin d'un créneau dont le retour n'est pas planifié (créneau ouvert). */
    public static final LocalDateTime OPEN_END = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private final TripR2dbcRepository tripRepository;
    private final AssignmentPersistencePort assignmentPort;
    private final AccessScopeService accessScope;
    private final ResourceLockPort resourceLock;
    private final DocumentPersistencePort documentPort;
    private final VehicleLocalR2dbcRepository vehicleRepository;
    private final VehicleTypeR2dbcRepository vehicleTypeRepository;

    public static LocalDateTime slotStart(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time != null ? time : LocalTime.MIDNIGHT);
    }

    public static LocalDateTime slotEnd(LocalDateTime scheduledReturn) {
        return scheduledReturn != null ? scheduledReturn : OPEN_END;
    }

    // ── Créneaux ─────────────────────────────────────────────────────────────

    /** Le binôme véhicule/chauffeur peut-il être engagé sur ce créneau de trajet ? */
    public Mono<Void> assertTripSlotFree(UUID vehicleId, UUID driverId,
                                         LocalDateTime start, LocalDateTime end,
                                         UUID excludeTripId) {
        LocalDateTime now = LocalDateTime.now();
        // Verrou véhicule + chauffeur jusqu'à la fin de la transaction appelante : une demande
        // concurrente sur l'une de ces ressources attend, puis voit l'engagement déjà pris.
        Mono<Void> lock = resourceLock.lock(vehicleId, driverId);
        Mono<Void> vehicleTrips = tripRepository
                .existsOverlappingTripForVehicle(vehicleId, start, end, now, excludeTripId)
                .flatMap(busy -> fail(busy, TripException::vehicleSlotConflict));
        Mono<Void> driverTrips = tripRepository
                .existsOverlappingTripForDriver(driverId, start, end, now, excludeTripId)
                .flatMap(busy -> fail(busy, TripException::driverSlotConflict));
        Mono<Void> vehicleAssignments = assignmentPort
                .findConflictingByVehicle(vehicleId, start, end, null)
                .filter(a -> !a.getDriverId().equals(driverId))
                .hasElements()
                .flatMap(busy -> fail(busy, TripException::vehicleSlotConflict));
        Mono<Void> driverAssignments = assignmentPort
                .findConflictingByDriver(driverId, start, end, null)
                .filter(a -> !a.getVehicleId().equals(vehicleId))
                .hasElements()
                .flatMap(busy -> fail(busy, TripException::driverSlotConflict));
        return lock.then(vehicleTrips).then(driverTrips).then(vehicleAssignments).then(driverAssignments);
    }

    /** Le binôme véhicule/chauffeur peut-il être affecté sur ce créneau ? */
    public Mono<Void> assertAssignmentSlotFree(UUID vehicleId, UUID driverId,
                                               LocalDateTime start, LocalDateTime end,
                                               UUID excludeAssignmentId) {
        Mono<Void> lock = resourceLock.lock(vehicleId, driverId);
        Mono<Void> vehicleAssignments = assignmentPort
                .findConflictingByVehicle(vehicleId, start, end, excludeAssignmentId)
                .hasElements()
                .flatMap(busy -> fail(busy, () -> PlanningException.vehicleConflict(
                        vehicleId, start.toString(), end.toString())));
        Mono<Void> driverAssignments = assignmentPort
                .findConflictingByDriver(driverId, start, end, excludeAssignmentId)
                .hasElements()
                .flatMap(busy -> fail(busy, () -> PlanningException.driverConflict(
                        driverId, start.toString(), end.toString())));
        Mono<Void> crossTrips = tripRepository
                .findOverlappingTripsForResources(vehicleId, driverId, start, end, LocalDateTime.now(), null)
                .flatMap(trip -> {
                    if (vehicleId.equals(trip.getVehicleId()) && !driverId.equals(trip.getDriverId())) {
                        return Mono.<Void>error(PlanningException.vehicleConflict(
                                vehicleId, start.toString(), end.toString()));
                    }
                    if (driverId.equals(trip.getDriverId()) && !vehicleId.equals(trip.getVehicleId())) {
                        return Mono.<Void>error(PlanningException.driverConflict(
                                driverId, start.toString(), end.toString()));
                    }
                    return Mono.<Void>empty();
                })
                .then();
        return lock.then(vehicleAssignments).then(driverAssignments).then(crossTrips);
    }

    // ── Permis de conduire ───────────────────────────────────────────────────

    /**
     * Le chauffeur est-il autorisé à conduire ce véhicule sur tout le créneau ? Il faut un permis
     * enregistré, valide jusqu'à la fin du créneau (son début s'il est ouvert) et couvrant la
     * catégorie exigée par le type du véhicule.
     */
    public Mono<Void> assertDriverLicensed(UUID driverId, UUID vehicleId,
                                           LocalDateTime start, LocalDateTime end) {
        java.time.LocalDate validOn = (end == null || OPEN_END.equals(end) ? start : end).toLocalDate();
        Mono<String> requiredCategory = vehicleRepository.findById(vehicleId)
                .filter(v -> v.getVehicleTypeId() != null)
                .flatMap(v -> vehicleTypeRepository.findById(v.getVehicleTypeId()))
                .map(VehicleTypeEntity::getRequiredLicenseCategory)
                .defaultIfEmpty("");
        Mono<java.util.List<DriverDocument>> licenses = documentPort.findDriverDocsByDriverId(driverId)
                .filter(d -> d.getDocType() == DriverDocument.DocType.DRIVING_LICENSE)
                .collectList();
        return Mono.zip(requiredCategory, licenses).flatMap(t -> {
            String required = t.getT1();
            var all = t.getT2();
            if (all.isEmpty()) {
                return Mono.error(DriverException.noDrivingLicense());
            }
            var valid = all.stream()
                    .filter(d -> d.getExpiryDate() == null || !d.getExpiryDate().isBefore(validOn))
                    .toList();
            if (valid.isEmpty()) {
                return Mono.error(DriverException.drivingLicenseExpired(validOn));
            }
            boolean covered = valid.stream()
                    .anyMatch(d -> DrivingLicenseRules.covers(d.getLicenseCategories(), required));
            return covered ? Mono.<Void>empty() : Mono.error(DriverException.drivingLicenseCategoryMissing(required));
        });
    }

    // ── Engagements ouverts ──────────────────────────────────────────────────

    /** Trajet non terminé (planifié ou en cours) ou affectation en attente / en cours. */
    public Mono<Boolean> vehicleHasOpenCommitments(UUID vehicleId) {
        return tripRepository.existsActiveTripForVehicle(vehicleId)
                .flatMap(hasTrip -> Boolean.TRUE.equals(hasTrip)
                        ? Mono.just(true)
                        : assignmentPort.findByVehicleId(vehicleId).any(Assignment::isActive));
    }

    public Mono<Boolean> driverHasOpenCommitments(UUID driverId) {
        return tripRepository.existsActiveTripForDriver(driverId)
                .flatMap(hasTrip -> Boolean.TRUE.equals(hasTrip)
                        ? Mono.just(true)
                        : assignmentPort.findByDriverId(driverId).any(Assignment::isActive));
    }

    // ── Chauffeur engageable ─────────────────────────────────────────────────

    /**
     * Un chauffeur ne peut être engagé (trajet, affectation, véhicule) que s'il est actif
     * (un chauffeur supprimé est INACTIVE) et rattaché à une flotte du périmètre de l'acteur.
     */
    public Mono<Void> assertDriverEngageable(UUID actorId, Driver driver,
                                             Supplier<? extends RuntimeException> onInactive) {
        if (!"ACTIVE".equals(driver.status()) || driver.fleetId() == null) {
            return Mono.error(onInactive.get());
        }
        return accessScope.assertFleetAccess(actorId, driver.fleetId());
    }

    private static Mono<Void> fail(Boolean condition, Supplier<? extends RuntimeException> error) {
        return Boolean.TRUE.equals(condition) ? Mono.error(error.get()) : Mono.empty();
    }
}
