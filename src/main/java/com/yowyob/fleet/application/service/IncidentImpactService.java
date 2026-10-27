package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.Assignment;
import com.yowyob.fleet.domain.model.Incident;
import com.yowyob.fleet.domain.ports.out.AssignmentPersistencePort;
import com.yowyob.fleet.domain.ports.out.IncidentPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Effets d'un incident sur la disponibilité du véhicule et sur la mission en cours.
 *
 * <ul>
 *   <li>Incident grave (HIGH/CRITICAL) ouvert → véhicule MAINTENANCE (indisponible pour tout
 *       nouveau trajet, départ ou affectation) jusqu'à la résolution.</li>
 *   <li>Trajet en cours sur ce véhicule → il reste ouvert et passe « interrompu » ; il reprend
 *       quand le gestionnaire affecte un véhicule de remplacement, ou quand l'incident est
 *       résolu.</li>
 *   <li>Résolution / clôture / suppression / baisse de gravité → le véhicule est libéré
 *       (ON_TRIP s'il est encore sur un trajet en cours, AVAILABLE sinon), sauf si un autre
 *       incident grave ouvert le maintient immobilisé.</li>
 * </ul>
 * Un véhicule déjà en MAINTENANCE pour une autre raison (maintenance planifiée…) n'est jamais
 * libéré par la résolution d'un incident : seul l'incident qui l'a immobilisé (champ
 * {@code vehicleImmobilized}) peut le libérer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentImpactService {

    private static final String MAINTENANCE = "MAINTENANCE";

    private final VehicleLocalR2dbcRepository vehicleRepository;
    private final TripR2dbcRepository tripRepository;
    private final IncidentPersistencePort incidentPersistence;
    private final AssignmentPersistencePort assignmentPort;

    /** Applique (ou lève) l'immobilisation selon l'état courant de l'incident, déjà enregistré. */
    public Mono<Incident> applyImpact(Incident incident) {
        if (incident.immobilizesVehicle() && !incident.isVehicleImmobilized()) {
            return immobilize(incident);
        }
        if (!incident.immobilizesVehicle() && incident.isVehicleImmobilized()) {
            return release(incident);
        }
        return Mono.just(incident);
    }

    /** À appeler avant la suppression d'un incident : libère le véhicule s'il le retenait. */
    public Mono<Void> releaseBeforeDelete(Incident incident) {
        return incident.isVehicleImmobilized() ? release(incident).then() : Mono.empty();
    }

    /**
     * Résumé des engagements du véhicule à réorganiser après son immobilisation, ajouté à la
     * notification du gestionnaire ; vide si rien n'est concerné.
     */
    public Mono<String> describeImpact(Incident incident) {
        if (!incident.isVehicleImmobilized()) {
            return Mono.just("");
        }
        LocalDateTime now = LocalDateTime.now();
        Mono<Long> inProgress = tripRepository.findInProgressTripsForVehicle(incident.getVehicleId()).count();
        Mono<Long> upcoming = tripRepository.countUpcomingScheduledTripsForVehicle(incident.getVehicleId(), now);
        Mono<Long> assignments = assignmentPort.findByVehicleId(incident.getVehicleId())
                .filter(a -> a.getStatus() == Assignment.Status.PENDING
                        || a.getStatus() == Assignment.Status.IN_PROGRESS)
                .filter(a -> a.getEndDatetime() == null || a.getEndDatetime().isAfter(now))
                .count();
        return Mono.zip(inProgress, upcoming, assignments).map(t -> {
            StringBuilder text = new StringBuilder(" Véhicule immobilisé jusqu'à la résolution de l'incident.");
            if (t.getT1() > 0) {
                text.append(" Trajet en cours interrompu : affectez un véhicule de remplacement pour reprendre"
                        + " la mission, ou annulez-la.");
            }
            if (t.getT2() > 0 || t.getT3() > 0) {
                text.append(String.format(" À réaffecter : %d trajet(s) planifié(s), %d affectation(s).",
                        t.getT2(), t.getT3()));
            }
            return text.toString();
        });
    }

    private Mono<Incident> immobilize(Incident incident) {
        UUID vehicleId = incident.getVehicleId();
        return vehicleRepository.findById(vehicleId)
                .filter(v -> v.getDeletedAt() == null)
                .flatMap(vehicle -> {
                    if (!MAINTENANCE.equals(vehicle.getStatus())) {
                        vehicle.setStatus(MAINTENANCE);
                        vehicle.setNew(false);
                        return vehicleRepository.save(vehicle).thenReturn(true);
                    }
                    // Déjà en MAINTENANCE : on ne partage l'immobilisation que si elle vient
                    // d'un autre incident (sinon c'est une maintenance, qu'on ne doit pas lever).
                    return otherImmobilizingIncident(incident).hasElement();
                })
                .flatMap(holds -> {
                    if (!Boolean.TRUE.equals(holds)) {
                        return Mono.just(incident);
                    }
                    incident.setVehicleImmobilized(true);
                    return interruptTrips(vehicleId, incident.getId())
                            .then(incidentPersistence.save(incident))
                            .doOnSuccess(i -> log.info("⛔ Véhicule {} immobilisé par l'incident {}",
                                    vehicleId, incident.getId()));
                })
                .defaultIfEmpty(incident);
    }

    private Mono<Incident> release(Incident incident) {
        UUID vehicleId = incident.getVehicleId();
        incident.setVehicleImmobilized(false);
        Mono<Void> freeVehicle = otherImmobilizingIncident(incident).hasElement()
                .flatMap(stillHeld -> Boolean.TRUE.equals(stillHeld)
                        ? Mono.<Void>empty()
                        : freeVehicle(vehicleId).then(resumeTrips(vehicleId)));
        return incidentPersistence.save(incident)
                .flatMap(saved -> freeVehicle.thenReturn(saved));
    }

    private Mono<Incident> otherImmobilizingIncident(Incident incident) {
        return incidentPersistence.findByVehicleId(incident.getVehicleId())
                .filter(other -> !other.getId().equals(incident.getId()))
                .filter(other -> other.isVehicleImmobilized() && other.immobilizesVehicle())
                .next();
    }

    private Mono<Void> freeVehicle(UUID vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .filter(v -> MAINTENANCE.equals(v.getStatus()))
                .flatMap(vehicle -> tripRepository.existsInProgressTripForVehicle(vehicleId, null)
                        .flatMap(onTrip -> {
                            vehicle.setStatus(Boolean.TRUE.equals(onTrip) ? "ON_TRIP" : "AVAILABLE");
                            vehicle.setNew(false);
                            return vehicleRepository.save(vehicle);
                        }))
                .doOnNext(v -> log.info("✅ Véhicule {} libéré après résolution d'incident → {}",
                        vehicleId, v.getStatus()))
                .then();
    }

    private Mono<Void> interruptTrips(UUID vehicleId, UUID incidentId) {
        LocalDateTime now = LocalDateTime.now();
        return tripRepository.findInProgressTripsForVehicle(vehicleId)
                .filter(trip -> !trip.isInterrupted())
                .flatMap(trip -> {
                    trip.setInterruptedAt(now);
                    trip.setResumedAt(null);
                    trip.setInterruptionIncidentId(incidentId);
                    trip.setNew(false);
                    return tripRepository.save(trip);
                })
                .then();
    }

    /** Le véhicule redevient utilisable : ses trajets en cours interrompus reprennent. */
    private Mono<Void> resumeTrips(UUID vehicleId) {
        LocalDateTime now = LocalDateTime.now();
        return tripRepository.findInProgressTripsForVehicle(vehicleId)
                .filter(TripEntity::isInterrupted)
                .flatMap(trip -> {
                    trip.setResumedAt(now);
                    trip.setNew(false);
                    return tripRepository.save(trip);
                })
                .then();
    }

    /** Vrai si le véhicule est retenu par un incident grave ouvert (empêche de le libérer à la main). */
    public Mono<Boolean> isHeldByIncident(VehicleLocalEntity vehicle) {
        return incidentPersistence.findByVehicleId(vehicle.getId())
                .any(i -> i.isVehicleImmobilized() && i.immobilizesVehicle());
    }
}
