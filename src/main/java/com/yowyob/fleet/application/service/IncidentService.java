package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.OperationException;
import com.yowyob.fleet.domain.model.AlertEvent;
import com.yowyob.fleet.domain.model.AlertRule;
import com.yowyob.fleet.domain.model.Coordinates;
import com.yowyob.fleet.domain.model.Incident;
import com.yowyob.fleet.domain.ports.in.ManageIncidentUseCase;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.IncidentPersistencePort;
import com.yowyob.fleet.domain.ports.out.OperationEventPort;
import com.yowyob.fleet.domain.ports.out.SendAlertPort;
import com.yowyob.fleet.domain.ports.out.VehiclePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentService implements ManageIncidentUseCase {

    private final IncidentPersistencePort incidentPersistence;
    private final VehiclePersistencePort vehiclePersistence;
    private final DriverPersistencePort driverPersistence;
    private final OperationEventPort eventPort;
    private final SendAlertPort sendAlertPort;
    private final UserLocalR2dbcRepository userRepo;
    private final IncidentCaseService caseService;
    private final IncidentImpactService incidentImpact;
    private final IncidentNotifier incidentNotifier;
    private final AccessScopeService accessScope;
    private final TripR2dbcRepository tripRepository;

    // ── 1. CRÉATION ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Incident> createIncident(CreateIncidentCommand cmd) {

        // 1. Vérification existence du véhicule
        java.util.concurrent.atomic.AtomicReference<UUID> vehicleFleet = new java.util.concurrent.atomic.AtomicReference<>();
        Mono<String> vehicleRegistrationMono = vehiclePersistence.getLocalDataById(cmd.vehicleId())
                .switchIfEmpty(Mono.error(OperationException.vehicleNotFound(cmd.vehicleId())))
                .doOnNext(v -> vehicleFleet.set(v.fleetId()))
                .map(v -> v.licensePlate());

        // 2. Chauffeur concerné : celui indiqué, sinon celui du trajet en cours sur ce véhicule
        //    (le gestionnaire qui déclare sur un véhicule en mission vise ce chauffeur-là).
        Mono<Optional<UUID>> driverIdMono = cmd.driverId() != null
                ? Mono.just(Optional.of(cmd.driverId()))
                : tripRepository.findInProgressTripsForVehicle(cmd.vehicleId()).next()
                        .map(t -> Optional.ofNullable(t.getDriverId()))
                        .defaultIfEmpty(Optional.empty());
        Mono<Optional<UUID>> reporterMono = accessScope.resolveLocalUserId(cmd.reporterId())
                .map(Optional::of).defaultIfEmpty(Optional.empty());

        // Un incident se déclare souvent après coup, jamais par avance.
        if (cmd.occurredAt() != null && cmd.occurredAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return Mono.error(OperationException.incidentDateInFuture());
        }

        return Mono.zip(vehicleRegistrationMono, driverIdMono, reporterMono).flatMap(head -> {
            UUID driverId = head.getT2().orElse(null);
            UUID reporter = head.getT3().orElse(null);
            Mono<String> driverNameMono = driverId != null
                    ? driverPersistence.findById(driverId)
                            .switchIfEmpty(Mono.error(OperationException.driverNotFound(driverId)))
                            // Chauffeur désigné : de la même flotte que le véhicule (séparation des flottes).
                            .flatMap(d -> cmd.driverId() != null && vehicleFleet.get() != null
                                    && !vehicleFleet.get().equals(d.fleetId())
                                    ? Mono.error(new com.yowyob.fleet.domain.exception.DriverException(
                                            "Ce chauffeur n'appartient pas à la flotte de ce véhicule.",
                                            org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "DRV_026"))
                                    : Mono.just(d))
                            .flatMap(d -> userRepo.findById(d.userId())
                                    .map(u -> String.join(" ",
                                            u.getFirstName() != null ? u.getFirstName() : "",
                                            u.getLastName() != null ? u.getLastName() : "").trim())
                                    .defaultIfEmpty(""))
                    : Mono.just("");
            return driverNameMono
                .flatMap(driverNameRaw -> {
                    String vehicleReg = head.getT1();
                    String driverName = driverNameRaw.isBlank() ? null : driverNameRaw;

                    Coordinates coords = buildCoordinates(cmd.longitude(), cmd.latitude());

                    Incident incident = new Incident(
                            null,
                            cmd.type(),
                            cmd.description(),
                            cmd.severity(),
                            cmd.occurredAt() != null ? cmd.occurredAt() : LocalDateTime.now(),
                            coords,
                            null,
                            cmd.reportedBy(),
                            cmd.vehicleId(),
                            vehicleReg,
                            driverId,
                            driverName
                    );

                    // Ajout du témoin si fourni
                    if (cmd.witnessName() != null || cmd.witnessContact() != null) {
                        incident.addWitness(cmd.witnessName(), cmd.witnessContact());
                    }

                    incident.setVehicleDrivable(cmd.vehicleDrivable() == null || cmd.vehicleDrivable());
                    return incidentPersistence.save(incident);
                })
                .flatMap(saved -> caseService.open(saved, cmd.reporterId(), cmd.details()).thenReturn(saved))
                // Incident grave, vol ou véhicule non roulant → immobilisé, trajet en cours interrompu
                // (le chauffeur du trajet est prévenu s'il n'est pas le déclarant).
                .flatMap(saved -> incidentImpact.applyImpact(saved, reporter))
                // Notification in-app immédiate : le bureau (et le chauffeur concerné si c'est le bureau qui déclare).
                .flatMap(saved -> incidentImpact.describeImpact(saved)
                        .flatMap(impact -> incidentNotifier.declared(saved, reporter, impact))
                        .onErrorResume(e -> Mono.empty())
                        .thenReturn(saved))
                .flatMap(saved -> {
                    // 3. Publication événement (Kafka, best-effort, no-op si Kafka indisponible)
                    // 4. Email au gestionnaire (toujours actif, ne dépend d'aucune AlertRule ni de Kafka)
                    vehiclePersistence.getLocalDataById(saved.getVehicleId())
                            .flatMap(v -> {
                                OperationEventPort.IncidentReportedEvent event =
                                        new OperationEventPort.IncidentReportedEvent(
                                                saved.getId(),
                                                saved.getType().name(),
                                                saved.getSeverity().name(),
                                                saved.getVehicleId(),
                                                saved.getVehicleRegistration(),
                                                saved.getDriverId(),
                                                v.managerId(),
                                                saved.isCritical()
                                        );
                                Mono<Void> kafkaPublish = eventPort.publishIncidentReported(event)
                                        .doOnError(e -> log.warn("⚠️ Échec publication événement incident {}: {}",
                                                saved.getId(), e.getMessage()))
                                        .onErrorResume(e -> Mono.empty());

                                Mono<Void> email = emailManagerOfIncident(saved, v.managerId());

                                return Mono.when(kafkaPublish, email);
                            })
                            .doOnError(e -> log.warn("⚠️ Échec notification incident {}: {}",
                                    saved.getId(), e.getMessage()))
                            .onErrorResume(e -> Mono.empty())
                            .subscribe();

                    if (saved.isCritical()) {
                        log.warn("🚨 Incident CRITIQUE déclaré sur le véhicule {} · Type: {}, Sévérité: {}",
                                saved.getVehicleRegistration(), saved.getType(), saved.getSeverity());
                    }

                    return Mono.just(saved);
                });
        });
    }

    // ── 2. LECTURE ───────────────────────────────────────────────────────────

    @Override
    public Mono<Incident> getById(UUID id) {
        return incidentPersistence.findById(id)
                .switchIfEmpty(Mono.error(OperationException.incidentNotFound(id)));
    }

    @Override
    public Flux<Incident> getAllByManager(UUID managerId) {
        return incidentPersistence.findAllByManagerId(managerId);
    }

    @Override
    public Flux<Incident> getByVehicleId(UUID vehicleId) {
        return vehiclePersistence.getLocalDataById(vehicleId)
                .switchIfEmpty(Mono.error(OperationException.vehicleNotFound(vehicleId)))
                .thenMany(incidentPersistence.findByVehicleId(vehicleId));
    }

    @Override
    public Flux<Incident> getByDriverId(UUID driverId) {
        return incidentPersistence.findByDriverId(driverId);
    }

    @Override
    public Flux<Incident> getByType(Incident.Type type, UUID managerId) {
        return incidentPersistence.findByType(type, managerId);
    }

    @Override
    public Flux<Incident> getBySeverity(Incident.Severity severity, UUID managerId) {
        return incidentPersistence.findBySeverity(severity, managerId);
    }

    @Override
    public Flux<Incident> getByStatus(Incident.Status status, UUID managerId) {
        return incidentPersistence.findByStatus(status, managerId);
    }

    @Override
    public Flux<Incident> getOpenIncidents(UUID managerId) {
        return incidentPersistence.findOpenIncidents(managerId);
    }

    @Override
    public Flux<Incident> getByDateRange(LocalDateTime start, LocalDateTime end, UUID managerId) {
        return incidentPersistence.findByDateRange(start, end, managerId);
    }

    // ── 3. MISE À JOUR ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Incident> update(UpdateIncidentCommand cmd) {
        return incidentPersistence.findById(cmd.incidentId())
                .switchIfEmpty(Mono.error(OperationException.incidentNotFound(cmd.incidentId())))
                .flatMap(incident -> {
                    // Vérification : un incident clôturé ne peut plus être modifié
                    if (incident.getStatus() == Incident.Status.CLOSED) {
                        return Mono.error(OperationException.incidentAlreadyClosed(cmd.incidentId()));
                    }

                    if (cmd.description() != null)          incident.setDescription(cmd.description());
                    if (cmd.severity() != null)             incident.setSeverity(cmd.severity());
                    if (cmd.report() != null)               incident.setReport(cmd.report());
                    if (cmd.policeReportNumber() != null)   incident.setPoliceReportNumber(cmd.policeReportNumber());
                    if (cmd.insuranceClaimNumber() != null) incident.setInsuranceClaimNumber(cmd.insuranceClaimNumber());

                    if (cmd.witnessName() != null || cmd.witnessContact() != null) {
                        incident.addWitness(cmd.witnessName(), cmd.witnessContact());
                    }

                    Coordinates coords = buildCoordinates(cmd.longitude(), cmd.latitude());
                    if (coords != null) incident.setLocation(coords);

                    // Une hausse ou une baisse de gravité immobilise ou libère le véhicule.
                    return incidentPersistence.save(incident)
                            .flatMap(incidentImpact::applyImpact);
                });
    }


    // ── 4. KPIs ──────────────────────────────────────────────────────────────

    @Override
    public Mono<Long> countByVehicleId(UUID vehicleId) {
        return incidentPersistence.countByVehicleId(vehicleId);
    }

    @Override
    public Mono<BigDecimal> getTotalCostByVehicleId(UUID vehicleId) {
        return incidentPersistence.getTotalCostByVehicleId(vehicleId)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    @Override
    public Mono<BigDecimal> getTotalCostByDriverId(UUID driverId) {
        return incidentPersistence.getTotalCostByDriverId(driverId)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    // ── HELPERS PRIVÉS ───────────────────────────────────────────────────────

    private Coordinates buildCoordinates(Double longitude, Double latitude) {
        if (longitude != null && latitude != null) {
            return new Coordinates(longitude, latitude);
        }
        return null;
    }

    /**
     * Email au gestionnaire, en plus de la notification in-app (IncidentNotifier) : toujours actif,
     * pas conditionné par une AlertRule configurable. Un échec d'envoi ne fait jamais échouer la
     * création de l'incident. L'alerte n'est plus enregistrée dans alert_events : la notification
     * in-app vit dans fleet.notifications (INCIDENT_DECLARED), sans doublon.
     */
    private Mono<Void> emailManagerOfIncident(Incident incident, UUID managerId) {
        if (managerId == null) {
            return Mono.empty();
        }
        String title = incident.isCritical() ? "Incident grave signalé" : "Incident signalé sur un véhicule";
        String base = String.format("%s (gravité %s) sur le véhicule %s.",
                incident.getType().label(), incident.getSeverity().label(), incident.getVehicleRegistration());
        return incidentImpact.describeImpact(incident).flatMap(impact -> {
            AlertEvent event = new AlertEvent(
                    null, null, null,
                    managerId, AlertRule.TriggerType.INCIDENT_REPORTED, AlertRule.ActionType.EMAIL,
                    title, base + impact,
                    incident.getId(), "INCIDENT",
                    AlertEvent.ReadStatus.UNREAD,
                    LocalDateTime.now(), null
            );
            return userRepo.findById(managerId)
                    .mapNotNull(u -> u.getEmail())
                    .flatMap(email -> sendAlertPort.sendEmail(event, email));
        }).doOnError(e -> log.warn("⚠️ Échec envoi email incident {} : {}", incident.getId(), e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }
}
