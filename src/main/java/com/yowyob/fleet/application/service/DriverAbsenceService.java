package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.DriverException;
import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.model.DriverAbsence;
import com.yowyob.fleet.domain.ports.out.AssignmentPersistencePort;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DriverAbsenceEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverAbsenceR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Absences datées des chauffeurs (congé, arrêt maladie, suspension). Une absence ne peut pas
 * recouvrir un trajet ou une affectation déjà prévus : le gestionnaire les réaffecte d'abord.
 * Le contrôle à l'engagement est fait par ResourceAvailabilityService.assertDriverPresent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverAbsenceService {

    private final DriverAbsenceR2dbcRepository absenceRepository;
    private final DriverPersistencePort driverPersistence;
    private final AccessScopeService accessScope;
    private final TripR2dbcRepository tripRepository;
    private final AssignmentPersistencePort assignmentPort;
    private final AppNotifier notifier;

    private static final java.time.format.DateTimeFormatter DAY = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record DeclareAbsenceCommand(String type, LocalDate startDate, LocalDate endDate, String reason) {}

    /** Le gestionnaire de la flotte, ou le chauffeur lui-même (lecture seule de ses absences). */
    public Flux<DriverAbsence> list(UUID driverId, UUID actorId) {
        return accessScope.resolveLocalUserId(actorId).defaultIfEmpty(new UUID(0, 0))
                .flatMap(localId -> localId.equals(driverId) ? Mono.<Void>empty() : requireDriverInScope(driverId, actorId).then())
                .thenMany(absenceRepository.findByDriverId(driverId))
                .map(DriverAbsenceEntity::toDomain);
    }

    /** Libellé français d'un type d'absence (texte des notifications). */
    public static String typeLabel(String type) {
        return switch (type == null ? "" : type) {
            case DriverAbsence.LEAVE -> "congé";
            case DriverAbsence.SICK_LEAVE -> "arrêt maladie";
            case DriverAbsence.SUSPENSION -> "suspension";
            default -> "absence";
        };
    }

    static String period(LocalDate start, LocalDate end) {
        return end == null ? "à partir du " + start.format(DAY) + " (sans date de fin)"
                : start.equals(end) ? "le " + start.format(DAY)
                : "du " + start.format(DAY) + " au " + end.format(DAY);
    }

    private Mono<Void> notifyDriver(UUID driverId, UUID actorId, String title, String message, AppNotifier.Level level,
                                    String event, UUID absenceId) {
        return accessScope.resolveLocalUserId(actorId).onErrorResume(e -> Mono.empty()).defaultIfEmpty(actorId)
                .flatMap(actor -> notifier.toUser(driverId, actor, title, message, level, event,
                        AppNotifier.refs("absenceId", absenceId, "driverId", driverId)));
    }

    /** Absences en cours ou à venir d'un ensemble de chauffeurs, groupées par chauffeur. */
    public Mono<Map<UUID, List<DriverAbsence>>> openAbsencesByDriver(Collection<UUID> driverIds, LocalDate from) {
        if (driverIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        return absenceRepository.findOpenForDrivers(driverIds, from)
                .map(DriverAbsenceEntity::toDomain)
                .collect(Collectors.groupingBy(DriverAbsence::driverId));
    }

    @Transactional
    public Mono<DriverAbsence> declare(UUID driverId, DeclareAbsenceCommand cmd, UUID actorId) {
        return declare(driverId, cmd, actorId, true);
    }

    /** {@code notifyDriver} faux : l'absence vient d'une demande du chauffeur, prévenu par la décision. */
    @Transactional
    public Mono<DriverAbsence> declare(UUID driverId, DeclareAbsenceCommand cmd, UUID actorId, boolean notifyDriver) {
        String type = cmd.type() == null ? "" : cmd.type().trim().toUpperCase(Locale.ROOT);
        if (!DriverAbsence.TYPES.contains(type)) {
            return Mono.error(DriverException.invalidAbsence("type inconnu (congé, arrêt maladie ou suspension)"));
        }
        if (cmd.startDate() == null) {
            return Mono.error(DriverException.invalidAbsence("la date de début est obligatoire"));
        }
        if (cmd.endDate() == null && !DriverAbsence.SUSPENSION.equals(type)) {
            return Mono.error(DriverException.invalidAbsence("la date de fin est obligatoire (seule une suspension peut rester ouverte)"));
        }
        if (cmd.endDate() != null && cmd.endDate().isBefore(cmd.startDate())) {
            return Mono.error(DriverException.invalidAbsence("la date de fin précède la date de début"));
        }
        String reason = cmd.reason() == null || cmd.reason().isBlank() ? null : cmd.reason().trim();
        if (DriverAbsence.SUSPENSION.equals(type) && reason == null) {
            return Mono.error(DriverException.invalidAbsence("indiquez le motif de la suspension"));
        }
        return requireDriverInScope(driverId, actorId)
                .flatMap(driver -> "LEFT".equals(driver.status())
                        ? Mono.<Driver>error(DriverException.left())
                        : Mono.just(driver))
                .then(assertNoOtherAbsence(driverId, cmd.startDate(), cmd.endDate(), null))
                .then(assertNoCommitments(driverId, cmd.startDate(), cmd.endDate()))
                .then(Mono.defer(() -> {
                    DriverAbsenceEntity entity = new DriverAbsenceEntity(
                            UUID.randomUUID(), driverId, type, cmd.startDate(), cmd.endDate(), reason,
                            actorId, Instant.now(), null, null, true);
                    return absenceRepository.save(entity);
                }))
                .map(DriverAbsenceEntity::toDomain)
                .flatMap(a -> !notifyDriver ? Mono.just(a) : notifyDriver(driverId, actorId, "Absence enregistrée",
                        "Votre gestionnaire a enregistré : " + typeLabel(a.type()) + " " + period(a.startDate(), a.endDate())
                                + (a.reason() != null ? " (" + a.reason() + ")" : "") + ". Aucun poste ni trajet ne vous sera confié sur ces jours.",
                        AppNotifier.Level.INFO, "ABSENCE_RECORDED", a.id()).thenReturn(a))
                .doOnSuccess(a -> log.info("📅 Absence {} déclarée pour le chauffeur {} ({} → {})",
                        type, driverId, a.startDate(), a.endDate()));
    }

    /** Annule une absence saisie par erreur (ou qui n'aura pas lieu). */
    public Mono<DriverAbsence> cancel(UUID driverId, UUID absenceId, UUID actorId) {
        return requireAbsence(driverId, absenceId, actorId)
                .flatMap(entity -> {
                    entity.setCancelledAt(Instant.now());
                    entity.setNew(false);
                    return absenceRepository.save(entity);
                })
                .map(DriverAbsenceEntity::toDomain)
                .flatMap(a -> notifyDriver(driverId, actorId, "Absence annulée",
                        "Votre " + typeLabel(a.type()) + " " + period(a.startDate(), a.endDate())
                                + " est annulée : vous redevenez disponible sur ces jours.",
                        AppNotifier.Level.WARNING, "ABSENCE_CANCELLED", a.id()).thenReturn(a));
    }

    /**
     * Fixe ou avance la date de fin (reprise anticipée, levée d'une suspension). Pour prolonger,
     * on déclare une nouvelle absence, ce qui revérifie les engagements sur les jours ajoutés.
     */
    public Mono<DriverAbsence> end(UUID driverId, UUID absenceId, LocalDate endDate, UUID actorId) {
        if (endDate == null) {
            return Mono.error(DriverException.invalidAbsence("la date de fin est obligatoire"));
        }
        return requireAbsence(driverId, absenceId, actorId)
                .flatMap(entity -> {
                    if (endDate.isBefore(entity.getStartDate())) {
                        return Mono.error(DriverException.invalidAbsence("la date de fin précède la date de début"));
                    }
                    if (entity.getEndDate() != null && endDate.isAfter(entity.getEndDate())) {
                        return Mono.error(DriverException.invalidAbsence(
                                "pour prolonger l'absence, déclarez une nouvelle période à la suite"));
                    }
                    entity.setEndDate(endDate);
                    entity.setNew(false);
                    return absenceRepository.save(entity);
                })
                .map(DriverAbsenceEntity::toDomain)
                .flatMap(a -> notifyDriver(driverId, actorId,
                        DriverAbsence.SUSPENSION.equals(a.type()) ? "Suspension levée" : "Fin d'absence avancée",
                        "Votre " + typeLabel(a.type()) + " se termine le " + endDate.format(DAY)
                                + " : vous êtes de nouveau disponible à partir du " + endDate.plusDays(1).format(DAY) + ".",
                        AppNotifier.Level.INFO, "ABSENCE_ENDED", a.id()).thenReturn(a));
    }

    private Mono<DriverAbsenceEntity> requireAbsence(UUID driverId, UUID absenceId, UUID actorId) {
        return requireDriverInScope(driverId, actorId)
                .then(absenceRepository.findById(absenceId))
                .filter(a -> driverId.equals(a.getDriverId()) && a.getCancelledAt() == null)
                .switchIfEmpty(Mono.error(DriverException.invalidAbsence("absence introuvable ou déjà annulée")));
    }

    private Mono<Driver> requireDriverInScope(UUID driverId, UUID actorId) {
        return driverPersistence.findById(driverId)
                .switchIfEmpty(Mono.error(DriverException.notFound()))
                .flatMap(driver -> driver.fleetId() == null
                        ? Mono.<Driver>error(DriverException.notInFleet())
                        : accessScope.assertFleetAccess(actorId, driver.fleetId()).thenReturn(driver));
    }

    private Mono<Void> assertNoOtherAbsence(UUID driverId, LocalDate from, LocalDate to, UUID excludeId) {
        return absenceRepository.findOverlapping(driverId, from, to != null ? to : LocalDate.of(9999, 12, 31))
                .filter(a -> !a.getId().equals(excludeId))
                .hasElements()
                .flatMap(busy -> Boolean.TRUE.equals(busy)
                        ? Mono.<Void>error(DriverException.absenceOverlap())
                        : Mono.<Void>empty());
    }

    /** Aucun trajet non terminé ni affectation active sur les jours de l'absence. */
    private Mono<Void> assertNoCommitments(UUID driverId, LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to != null ? to.plusDays(1).atStartOfDay() : ResourceAvailabilityService.OPEN_END;
        return tripRepository.existsOverlappingTripForDriver(driverId, start, end, LocalDateTime.now(), null)
                .flatMap(busy -> Boolean.TRUE.equals(busy)
                        ? Mono.just(true)
                        : assignmentPort.findConflictingByDriver(driverId, start, end, null).hasElements())
                .flatMap(busy -> Boolean.TRUE.equals(busy)
                        ? Mono.<Void>error(DriverException.absenceOverlapsCommitments())
                        : Mono.<Void>empty());
    }
}
