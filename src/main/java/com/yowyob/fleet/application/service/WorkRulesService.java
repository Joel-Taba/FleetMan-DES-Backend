package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.PlanningException;
import com.yowyob.fleet.domain.model.Assignment;
import com.yowyob.fleet.domain.model.WorkTimeRules;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Temps de travail des chauffeurs (règles de l'entreprise) et traçabilité des postes : motif d'un
 * dépassement, changements après publication, notification du chauffeur, accusé de réception.
 */
@Service
@RequiredArgsConstructor
public class WorkRulesService {

    public record Extras(java.time.LocalDateTime acknowledgedAt, String changeReason, java.time.LocalDateTime changedAt,
                         String workRuleOverride, String cancelReason) {}

    private static final UUID NONE = new UUID(0, 0);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm");

    private final DatabaseClient db;
    private final AppNotifier notifier;

    /** Nature d'un changement communiqué au chauffeur (clé « change » de la notification SHIFT_CHANGED). */
    public enum ShiftChangeKind { NEW, MODIFIED, REMOVED, CANCELLED, DELETED }

    public Mono<WorkTimeRules.Limits> limitsOfCompany(UUID companyId) {
        return db.sql("SELECT max_shift_hours, min_rest_hours, max_weekly_hours, max_consecutive_days FROM fleet.companies WHERE id = :c")
                .bind("c", companyId)
                .map((row, meta) -> new WorkTimeRules.Limits(
                        row.get("max_shift_hours", BigDecimal.class).doubleValue(),
                        row.get("min_rest_hours", BigDecimal.class).doubleValue(),
                        row.get("max_weekly_hours", BigDecimal.class).doubleValue(),
                        row.get("max_consecutive_days", Integer.class)))
                .one()
                .defaultIfEmpty(WorkTimeRules.Limits.DEFAULT);
    }

    public Mono<WorkTimeRules.Limits> limitsOfFleet(UUID fleetId) {
        return db.sql("SELECT company_id FROM fleet.fleets WHERE id = :f")
                .bind("f", fleetId)
                .map((row, meta) -> row.get("company_id", UUID.class))
                .one()
                .flatMap(this::limitsOfCompany)
                .defaultIfEmpty(WorkTimeRules.Limits.DEFAULT);
    }

    public Mono<WorkTimeRules.Limits> updateLimits(UUID companyId, WorkTimeRules.Limits limits) {
        if (limits.maxShiftHours() < 1 || limits.maxShiftHours() > 24
                || limits.minRestHours() < 0 || limits.minRestHours() > 24
                || limits.maxWeeklyHours() < 1 || limits.maxWeeklyHours() > 168
                || limits.maxConsecutiveDays() < 1 || limits.maxConsecutiveDays() > 31) {
            return Mono.error(PlanningException.invalidWorkRules(
                    "Valeurs admises : poste de 1 à 24 h, repos de 0 à 24 h, semaine de 1 à 168 h, 1 à 31 jours d'affilée."));
        }
        return db.sql("""
                UPDATE fleet.companies SET max_shift_hours = :shift, min_rest_hours = :rest, max_weekly_hours = :weekly,
                       max_consecutive_days = :days, updated_at = NOW() WHERE id = :c
                """)
                .bind("shift", BigDecimal.valueOf(limits.maxShiftHours()))
                .bind("rest", BigDecimal.valueOf(limits.minRestHours()))
                .bind("weekly", BigDecimal.valueOf(limits.maxWeeklyHours()))
                .bind("days", limits.maxConsecutiveDays())
                .bind("c", companyId)
                .fetch().rowsUpdated()
                .then(limitsOfCompany(companyId));
    }

    /** Dépassements que provoquerait ce poste pour le chauffeur (postes et trajets hors poste). */
    public Mono<List<WorkTimeRules.Violation>> check(UUID fleetId, UUID driverId, LocalDateTime start, LocalDateTime end, UUID excludeAssignmentId) {
        LocalDateTime from = start.minusDays(8);
        LocalDateTime to = end.plusDays(8);
        Mono<List<WorkTimeRules.Interval>> others = db.sql("""
                SELECT start_datetime AS s, end_datetime AS e FROM fleet.assignments
                WHERE driver_id = :driver AND id <> :exclude AND status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED')
                  AND end_datetime > :from AND start_datetime < :to
                UNION ALL
                SELECT t.start_date + COALESCE(t.start_time, TIME '00:00') AS s,
                       COALESCE(t.return_registered_at, t.scheduled_return_datetime,
                                t.start_date + COALESCE(t.start_time, TIME '00:00') + INTERVAL '1 hour') AS e
                FROM fleet.trips t
                WHERE t.driver_id = :driver AND t.assignment_id IS NULL AND t.deleted_at IS NULL
                  AND t.status NOT IN ('CANCELLED', 'MISSED')
                  AND t.start_date BETWEEN CAST(:from AS DATE) AND CAST(:to AS DATE)
                """)
                .bind("driver", driverId)
                .bind("exclude", excludeAssignmentId != null ? excludeAssignmentId : NONE)
                .bind("from", from)
                .bind("to", to)
                .map((row, meta) -> new WorkTimeRules.Interval(row.get("s", LocalDateTime.class), row.get("e", LocalDateTime.class)))
                .all()
                .filter(i -> i.start() != null && i.end() != null && i.end().isAfter(i.start()))
                .collectList();
        return Mono.zip(limitsOfFleet(fleetId), others)
                .map(t -> WorkTimeRules.check(new WorkTimeRules.Interval(start, end), t.getT2(), t.getT1()));
    }

    /**
     * Avertissement bloquant tant qu'aucun motif n'est donné : renvoie le motif à conserver sur le
     * poste (vide s'il n'y a aucun dépassement).
     */
    public Mono<String> enforce(UUID fleetId, UUID driverId, LocalDateTime start, LocalDateTime end, UUID excludeId, String override) {
        return check(fleetId, driverId, start, end, excludeId).flatMap(violations -> {
            if (violations.isEmpty()) return Mono.just("");
            String reason = clean(override);
            if (reason == null) {
                return Mono.error(PlanningException.workRulesExceeded(
                        violations.stream().map(WorkTimeRules.Violation::message).collect(Collectors.joining(" "))));
            }
            return Mono.just(reason);
        });
    }

    /**
     * Modification d'un poste : seul un dépassement que le changement crée (absent avec les
     * anciens horaires du même chauffeur) exige un motif ; un dépassement déjà accepté ne revient pas.
     */
    public Mono<String> enforceChange(UUID fleetId, UUID driverId, LocalDateTime start, LocalDateTime end, UUID assignmentId,
                                      String override, UUID previousDriverId, LocalDateTime previousStart, LocalDateTime previousEnd) {
        Mono<java.util.Set<String>> before = driverId.equals(previousDriverId)
                ? check(fleetId, driverId, previousStart, previousEnd, assignmentId)
                        .map(v -> v.stream().map(WorkTimeRules.Violation::code).collect(Collectors.toSet()))
                : Mono.just(java.util.Set.of());
        return Mono.zip(before, check(fleetId, driverId, start, end, assignmentId)).flatMap(t -> {
            List<WorkTimeRules.Violation> added = t.getT2().stream().filter(v -> !t.getT1().contains(v.code())).toList();
            if (added.isEmpty()) return Mono.just("");
            String reason = clean(override);
            if (reason == null) {
                return Mono.error(PlanningException.workRulesExceeded(
                        added.stream().map(WorkTimeRules.Violation::message).collect(Collectors.joining(" "))));
            }
            return Mono.just(reason);
        });
    }

    public Mono<Boolean> isPublished(UUID scheduleId) {
        if (scheduleId == null) return Mono.just(false);
        return db.sql("SELECT status FROM fleet.schedules WHERE id = :id")
                .bind("id", scheduleId)
                .map((row, meta) -> "PUBLISHED".equals(row.get("status", String.class)))
                .one()
                .defaultIfEmpty(false);
    }

    /**
     * Le chauffeur voit-il ce poste ? Un poste hors planning est visible dès sa création, celui
     * d'un planning dès la publication (un brouillon reste invisible).
     */
    public Mono<Boolean> isVisibleToDriver(UUID scheduleId) {
        if (scheduleId == null) return Mono.just(true);
        return db.sql("SELECT status FROM fleet.schedules WHERE id = :id")
                .bind("id", scheduleId)
                .map((row, meta) -> !"DRAFT".equals(row.get("status", String.class)))
                .one()
                .defaultIfEmpty(true);
    }

    /** Motif exigé pour toucher un poste d'un planning publié. */
    public Mono<String> requireReasonIfPublished(UUID scheduleId, String reason) {
        return isPublished(scheduleId).flatMap(published -> {
            String cleaned = clean(reason);
            if (published && cleaned == null) return Mono.error(PlanningException.changeReasonRequired());
            return Mono.just(cleaned != null ? cleaned : "");
        });
    }

    public Mono<Void> recordOverride(UUID assignmentId, String override) {
        if (override == null || override.isBlank()) return Mono.empty();
        return db.sql("UPDATE fleet.assignments SET work_rule_override = :o WHERE id = :id")
                .bind("o", override).bind("id", assignmentId)
                .fetch().rowsUpdated().then();
    }

    /** Changement après publication : motif tracé, accusé de réception à refaire, alerte de non-lecture réarmée. */
    public Mono<Void> recordChange(UUID assignmentId, String reason) {
        return db.sql("UPDATE fleet.assignments SET change_reason = :r, changed_at = :now, acknowledged_at = NULL, "
                        + "unack_alerted_at = NULL WHERE id = :id")
                .bind("r", reason).bind("now", LocalDateTime.now()).bind("id", assignmentId)
                .fetch().rowsUpdated().then();
    }

    public Mono<Void> recordCancel(UUID assignmentId, String reason) {
        if (reason == null || reason.isBlank()) return Mono.empty();
        return db.sql("UPDATE fleet.assignments SET cancel_reason = :r, change_reason = :r, changed_at = :now WHERE id = :id")
                .bind("r", reason).bind("now", LocalDateTime.now()).bind("id", assignmentId)
                .fetch().rowsUpdated().then();
    }

    /** Poste marqué absent par le gestionnaire : la tâche planifiée ne renverra pas la notification. */
    public Mono<Void> recordNoShow(UUID assignmentId) {
        return db.sql("UPDATE fleet.assignments SET no_show_notified_at = :now WHERE id = :id")
                .bind("now", LocalDateTime.now()).bind("id", assignmentId)
                .fetch().rowsUpdated().then();
    }

    /** Le chauffeur est prévenu qu'il a été marqué absent à son poste. */
    public Mono<Void> notifyNoShow(Assignment a, String reason) {
        String why = clean(reason);
        return notifier.toUser(a.getDriverId(), null, "Absence au poste",
                "Vous avez été marqué absent au poste " + describe(a) + (why != null ? " : " + why : ".")
                        + " Contactez votre gestionnaire si c'est une erreur.",
                AppNotifier.Level.WARNING, "SHIFT_NO_SHOW",
                AppNotifier.refs("assignmentId", a.getId(), "scheduleId", a.getScheduleId()));
    }

    /** Notification SHIFT_CHANGED historique (nouveau poste, modification, retrait, annulation, suppression). */
    public Mono<Void> notifyDriver(UUID driverId, String title, String message, UUID scheduleId, UUID assignmentId) {
        return notifyShift(driverId, title, message, ShiftChangeKind.MODIFIED, scheduleId, assignmentId);
    }

    /**
     * Prévient le chauffeur d'un changement de poste. Pour un poste supprimé ou retiré, l'identifiant
     * du poste n'est pas joint : le chauffeur ne peut plus l'ouvrir.
     */
    public Mono<Void> notifyShift(UUID driverId, String title, String message, ShiftChangeKind kind,
                                  UUID scheduleId, UUID assignmentId) {
        boolean gone = kind == ShiftChangeKind.DELETED || kind == ShiftChangeKind.REMOVED;
        AppNotifier.Level level = kind == ShiftChangeKind.NEW ? AppNotifier.Level.INFO : AppNotifier.Level.WARNING;
        return notifier.toUser(driverId, null, title, message, level, "SHIFT_CHANGED",
                AppNotifier.refs("assignmentId", gone ? null : assignmentId, "scheduleId", scheduleId,
                        "change", kind.name()));
    }

    /** Nombre de postes à venir d'un planning et nombre de ceux dont le chauffeur a accusé réception. */
    public record AckSummary(long upcoming, long acknowledged, long changedUnacknowledged) {}

    public Mono<AckSummary> ackSummary(UUID scheduleId) {
        return db.sql("""
                SELECT COUNT(*) AS n,
                       COUNT(acknowledged_at) AS acked,
                       COUNT(*) FILTER (WHERE acknowledged_at IS NULL AND changed_at IS NOT NULL) AS changed
                FROM fleet.assignments WHERE schedule_id = :s AND status IN ('PENDING', 'IN_PROGRESS')
                """)
                .bind("s", scheduleId)
                .map((row, meta) -> new AckSummary(row.get("n", Long.class), row.get("acked", Long.class),
                        row.get("changed", Long.class)))
                .one()
                .defaultIfEmpty(new AckSummary(0, 0, 0));
    }

    public static String describe(Assignment a) {
        return "le " + a.getStartDatetime().format(WHEN) + " → " + a.getEndDatetime().toLocalTime();
    }

    public Mono<Extras> extras(UUID assignmentId) {
        return db.sql("SELECT acknowledged_at, change_reason, changed_at, work_rule_override, cancel_reason FROM fleet.assignments WHERE id = :id")
                .bind("id", assignmentId)
                .map((row, meta) -> new Extras(row.get("acknowledged_at", LocalDateTime.class), row.get("change_reason", String.class),
                        row.get("changed_at", LocalDateTime.class), row.get("work_rule_override", String.class),
                        row.get("cancel_reason", String.class)))
                .one()
                .defaultIfEmpty(new Extras(null, null, null, null, null));
    }

    /**
     * Accusé de réception : tous les postes publiés du chauffeur dans ce planning, ou un poste
     * (y compris un poste hors planning, visible dès sa création).
     */
    public Mono<Long> acknowledge(UUID driverId, UUID scheduleId, UUID assignmentId) {
        String filter = assignmentId != null ? "a.id = :target" : "a.schedule_id = :target";
        return db.sql("""
                UPDATE fleet.assignments a SET acknowledged_at = :now
                WHERE a.driver_id = :driver AND a.acknowledged_at IS NULL AND %s
                  AND (a.schedule_id IS NULL OR EXISTS (SELECT 1 FROM fleet.schedules s
                                                        WHERE s.id = a.schedule_id AND s.status <> 'DRAFT'))
                """.formatted(filter))
                .bind("now", LocalDateTime.now()).bind("driver", driverId)
                .bind("target", assignmentId != null ? assignmentId : scheduleId)
                .fetch().rowsUpdated();
    }

    public Flux<UUID> driversOfSchedule(UUID scheduleId) {
        return db.sql("SELECT DISTINCT driver_id FROM fleet.assignments WHERE schedule_id = :s")
                .bind("s", scheduleId)
                .map((row, meta) -> row.get("driver_id", UUID.class))
                .all();
    }

    static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
