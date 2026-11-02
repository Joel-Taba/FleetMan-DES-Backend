package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.PlanningException;
import com.yowyob.fleet.domain.exception.TripException;
import com.yowyob.fleet.domain.model.Assignment;
import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.ports.out.AssignmentPersistencePort;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.ResourceLockPort;
import com.yowyob.fleet.domain.exception.DriverException;
import com.yowyob.fleet.domain.model.DocumentCatalog;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DocumentEntity;
import com.yowyob.fleet.domain.model.DrivingLicenseRules;
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
    private final DocumentComplianceService documentCompliance;
    private final VehicleLocalR2dbcRepository vehicleRepository;
    private final VehicleTypeR2dbcRepository vehicleTypeRepository;
    private final com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverAbsenceR2dbcRepository absenceRepository;
    private final org.springframework.r2dbc.core.DatabaseClient db;

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

    // ── Flotte opérationnelle ────────────────────────────────────────────────

    /**
     * Une flotte dont le gestionnaire est suspendu (ou clôturé) est gelée : aucun nouveau trajet,
     * départ ni affectation tant que l'administrateur ne l'a pas réactivé ou n'a pas transféré la
     * flotte. Les trajets déjà partis peuvent se terminer (retour non concerné).
     */
    public Mono<Void> assertFleetOperable(UUID fleetId) {
        if (fleetId == null) {
            return Mono.empty();
        }
        return db.sql("""
                        SELECT f.name, fm.account_status
                        FROM fleet.fleets f LEFT JOIN fleet.fleet_managers fm ON fm.user_id = f.manager_id
                        WHERE f.id = :f
                        """)
                .bind("f", fleetId)
                .fetch().one()
                .flatMap(row -> {
                    Object status = row.get("account_status");
                    return status == null || "ACTIVE".equals(status.toString())
                            ? Mono.<Void>empty()
                            : Mono.<Void>error(com.yowyob.fleet.domain.exception.FleetException.frozen(
                                    String.valueOf(row.get("name"))));
                });
    }

    // ── Équipage qualifié ────────────────────────────────────────────────────

    /**
     * Le binôme peut-il partir sur ce créneau ? Chauffeur présent (ni congé, ni arrêt, ni
     * suspension sur ces jours), permis valide couvrant la catégorie du véhicule, et véhicule
     * en règle (assurance, visite technique, carte grise valides jusqu'à la fin du créneau).
     */
    public Mono<Void> assertCrewQualified(UUID driverId, UUID vehicleId,
                                          LocalDateTime start, LocalDateTime end) {
        return assertDriverPresent(driverId, start, end)
                .then(assertDriverLicensed(driverId, vehicleId, start, end))
                .then(assertDriverFileCompliant(driverId, start, end))
                .then(assertVehicleCompliant(vehicleId, start, end));
    }

    /** Aucune absence (congé, arrêt maladie, suspension) sur les jours du créneau. */
    public Mono<Void> assertDriverPresent(UUID driverId, LocalDateTime start, LocalDateTime end) {
        java.time.LocalDate from = start.toLocalDate();
        java.time.LocalDate to = (end == null || OPEN_END.equals(end) ? start : end).toLocalDate();
        return absenceRepository.findOverlapping(driverId, from, to)
                .next()
                .flatMap(a -> Mono.<Void>error(DriverException.absent(a.getType(), a.getStartDate(), a.getEndDate())));
    }

    /** Pièces exigées du véhicule (socle légal + entreprise) approuvées et valides jusqu'à la fin du créneau. */
    public Mono<Void> assertVehicleCompliant(UUID vehicleId, LocalDateTime start, LocalDateTime end) {
        java.time.LocalDate validOn = (end == null || OPEN_END.equals(end) ? start : end).toLocalDate();
        return documentCompliance.gaps(DocumentCatalog.TargetType.VEHICLE, vehicleId, validOn)
                .flatMap(gaps -> gaps.isEmpty()
                        ? Mono.<Void>empty()
                        : Mono.<Void>error(com.yowyob.fleet.domain.exception.VehicleException.notCompliant(
                                gaps.stream()
                                        .map(g -> DocumentComplianceService.describe(DocumentCatalog.TargetType.VEHICLE, g))
                                        .collect(java.util.stream.Collectors.joining(", ")))));
    }

    /** Autres pièces exigées du chauffeur par l'entreprise (visite médicale, carte professionnelle…). */
    public Mono<Void> assertDriverFileCompliant(UUID driverId, LocalDateTime start, LocalDateTime end) {
        java.time.LocalDate validOn = (end == null || OPEN_END.equals(end) ? start : end).toLocalDate();
        return documentCompliance.gaps(DocumentCatalog.TargetType.DRIVER, driverId, validOn)
                .flatMap(gaps -> {
                    var others = gaps.stream().filter(g -> !DocumentCatalog.DRIVING_LICENSE.equals(g.docType())).toList();
                    return others.isEmpty()
                            ? Mono.<Void>empty()
                            : Mono.<Void>error(DriverException.documentsNotCompliant(others.stream()
                                    .map(g -> DocumentComplianceService.describe(DocumentCatalog.TargetType.DRIVER, g))
                                    .collect(java.util.stream.Collectors.joining(", "))));
                });
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
        Mono<java.util.List<DocumentEntity>> licenses = documentCompliance.licensesInForce(driverId).collectList();
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
    /**
     * Affectation qui engage encore la ressource : en cours, ou en attente sur une période pas
     * encore terminée. Une affectation jamais démarrée dont la fin est passée n'engage plus rien
     * (elle bloquait indéfiniment la vente d'un véhicule ou le départ d'un chauffeur).
     */
    static boolean isOpenCommitment(Assignment a) {
        return a.getStatus() == Assignment.Status.IN_PROGRESS
                || (a.getStatus() == Assignment.Status.PENDING
                    && (a.getEndDatetime() == null || a.getEndDatetime().isAfter(LocalDateTime.now())));
    }

    public Mono<Boolean> vehicleHasOpenCommitments(UUID vehicleId) {
        return tripRepository.existsActiveTripForVehicle(vehicleId)
                .flatMap(hasTrip -> Boolean.TRUE.equals(hasTrip)
                        ? Mono.just(true)
                        : assignmentPort.findByVehicleId(vehicleId).any(ResourceAvailabilityService::isOpenCommitment));
    }

    public Mono<Boolean> driverHasOpenCommitments(UUID driverId) {
        return tripRepository.existsActiveTripForDriver(driverId)
                .flatMap(hasTrip -> Boolean.TRUE.equals(hasTrip)
                        ? Mono.just(true)
                        : assignmentPort.findByDriverId(driverId).any(ResourceAvailabilityService::isOpenCommitment));
    }

    // ── Chauffeur engageable ─────────────────────────────────────────────────

    /**
     * Un chauffeur ne peut être engagé (trajet, affectation, véhicule) que s'il fait partie des
     * effectifs (pas LEFT) et est rattaché à une flotte du périmètre de l'acteur.
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
