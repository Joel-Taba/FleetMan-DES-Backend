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
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    // ── 1. CRÉATION ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Incident> createIncident(CreateIncidentCommand cmd) {

        // 1. Vérification existence du véhicule
        Mono<String> vehicleRegistrationMono = vehiclePersistence.getLocalDataById(cmd.vehicleId())
                .switchIfEmpty(Mono.error(OperationException.vehicleNotFound(cmd.vehicleId())))
                .map(v -> v.licensePlate());

        // 2. Résolution optionnelle du nom du chauffeur
        Mono<String> driverNameMono = cmd.driverId() != null
                ? driverPersistence.findById(cmd.driverId())
                        .switchIfEmpty(Mono.error(OperationException.driverNotFound(cmd.driverId())))
                        .flatMap(d -> userRepo.findById(d.userId())
                                .map(u -> String.join(" ",
                                        u.getFirstName() != null ? u.getFirstName() : "",
                                        u.getLastName() != null ? u.getLastName() : "").trim())
                                .defaultIfEmpty(""))
                : Mono.just("");

        // Un incident se déclare souvent après coup, jamais par avance.
        if (cmd.occurredAt() != null && cmd.occurredAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return Mono.error(OperationException.incidentDateInFuture());
        }

        return Mono.zip(vehicleRegistrationMono, driverNameMono)
                .flatMap(tuple -> {
                    String vehicleReg = tuple.getT1();
                    String driverName = tuple.getT2().isBlank() ? null : tuple.getT2();

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
                            cmd.driverId(),
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
                // Incident grave, vol ou véhicule non roulant → immobilisé, trajet en cours interrompu.
                .flatMap(incidentImpact::applyImpact)
                .flatMap(saved -> {
                    // 3. Publication événement (Kafka, best-effort — no-op si Kafka indisponible)
                    // 4. Notification in-app immédiate au Fleet Manager (toujours active,
                    //    ne dépend d'aucune AlertRule configurable ni de Kafka)
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

                                Mono<AlertEvent> notify = notifyManagerOfIncident(saved, v.managerId());

                                return Mono.when(kafkaPublish, notify);
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

    private Mono<AlertEvent> notifyManagerOfIncident(Incident incident, java.util.UUID managerId) {
        String title = incident.isCritical()
                ? "Incident grave signalé"
                : "Incident signalé sur un véhicule";
        String base = String.format("%s (gravité %s) sur le véhicule %s.",
                incident.getType().label(), incident.getSeverity().label(), incident.getVehicleRegistration());

        // Si le véhicule est immobilisé : trajets / affectations à réorganiser.
        return incidentImpact.describeImpact(incident).flatMap(impact -> {
            AlertEvent event = new AlertEvent(
                    null, null, null,
                    managerId, AlertRule.TriggerType.INCIDENT_REPORTED, AlertRule.ActionType.IN_APP_NOTIFICATION,
                    title, base + impact,
                    incident.getId(), "INCIDENT",
                    AlertEvent.ReadStatus.UNREAD,
                    java.time.LocalDateTime.now(), null
            );
            return sendAlertPort.sendInApp(event)
                    .doOnSuccess(saved -> notifyManagerByEmail(saved, managerId));
        });
    }

    /**
     * Email au gestionnaire, en plus du in-app — toujours actif comme le in-app
     * (pas conditionné par une AlertRule configurable). Fire & forget : un échec
     * d'envoi email ne doit jamais faire échouer la création de l'incident.
     */
    private void notifyManagerByEmail(AlertEvent event, java.util.UUID managerId) {
        userRepo.findById(managerId)
                .mapNotNull(u -> u.getEmail())
                .flatMap(email -> sendAlertPort.sendEmail(event, email))
                .doOnError(e -> log.warn("⚠️ Échec envoi email incident {} : {}", event.getId(), e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }
}
