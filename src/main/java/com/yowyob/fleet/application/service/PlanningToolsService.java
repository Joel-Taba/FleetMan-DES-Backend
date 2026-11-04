package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.DomainException;
import com.yowyob.fleet.domain.exception.PlanningException;
import com.yowyob.fleet.domain.model.Assignment;
import com.yowyob.fleet.domain.model.Schedule;
import com.yowyob.fleet.domain.ports.in.ManageAssignmentUseCase;
import com.yowyob.fleet.domain.ports.out.AssignmentPersistencePort;
import com.yowyob.fleet.domain.ports.out.SchedulePersistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Outils de planification : modèles de postes, création en série, duplication d'un planning. */
@Service
@RequiredArgsConstructor
public class PlanningToolsService {

    public record ShiftTemplate(UUID id, String name, LocalTime startTime, LocalTime endTime, int sortOrder) {}

    public record BulkCommand(UUID scheduleId, UUID fleetId, UUID vehicleId, UUID driverId, List<LocalDate> dates,
                              LocalTime startTime, LocalTime endTime, String notes, String workRuleOverride, String changeReason) {}

    public record SkippedShift(UUID driverId, UUID vehicleId, LocalDateTime start, String reason) {}

    public record DuplicateResult(Schedule schedule, int copied, List<SkippedShift> skipped) {}

    private static final int MAX_BULK_DAYS = 62;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private final DatabaseClient db;
    private final ManageAssignmentUseCase assignments;
    private final AssignmentPersistencePort assignmentPort;
    private final SchedulePersistencePort schedules;

    // ── Modèles de postes ────────────────────────────────────────────────────

    public Flux<ShiftTemplate> templates(UUID companyId) {
        return db.sql("SELECT COUNT(*) AS n FROM fleet.shift_templates WHERE company_id = :c")
                .bind("c", companyId)
                .map((row, meta) -> row.get("n", Long.class))
                .one()
                .flatMap(n -> n > 0 ? Mono.<Void>empty() : seedDefaults(companyId))
                .thenMany(Flux.defer(() -> db.sql("""
                        SELECT id, name, start_time, end_time, sort_order FROM fleet.shift_templates
                        WHERE company_id = :c ORDER BY sort_order, start_time
                        """)
                        .bind("c", companyId)
                        .map((row, meta) -> new ShiftTemplate(row.get("id", UUID.class), row.get("name", String.class),
                                row.get("start_time", LocalTime.class), row.get("end_time", LocalTime.class),
                                row.get("sort_order", Integer.class)))
                        .all()));
    }

    public Mono<ShiftTemplate> saveTemplate(UUID companyId, UUID id, String name, LocalTime start, LocalTime end) {
        String cleaned = name == null ? "" : name.trim();
        if (cleaned.isEmpty() || cleaned.length() > 60) {
            return Mono.error(PlanningException.invalidTemplate("Le nom du modèle doit compter de 1 à 60 caractères."));
        }
        if (start == null || end == null || start.equals(end)) {
            return Mono.error(PlanningException.invalidTemplate("Indiquez une heure de début et une heure de fin différentes."));
        }
        Mono<Long> write = id == null
                ? db.sql("""
                        INSERT INTO fleet.shift_templates (id, company_id, name, start_time, end_time, sort_order)
                        SELECT :id, :c, :name, :start, :end, COALESCE(MAX(sort_order), 0) + 1 FROM fleet.shift_templates WHERE company_id = :c
                        """)
                        .bind("id", UUID.randomUUID()).bind("c", companyId).bind("name", cleaned).bind("start", start).bind("end", end)
                        .fetch().rowsUpdated()
                : db.sql("UPDATE fleet.shift_templates SET name = :name, start_time = :start, end_time = :end WHERE id = :id AND company_id = :c")
                        .bind("id", id).bind("c", companyId).bind("name", cleaned).bind("start", start).bind("end", end)
                        .fetch().rowsUpdated();
        return write
                .onErrorMap(org.springframework.dao.DataIntegrityViolationException.class,
                        e -> PlanningException.invalidTemplate("Un modèle porte déjà ce nom."))
                .flatMap(n -> n == 0 ? Mono.error(PlanningException.invalidTemplate("Modèle introuvable.")) : Mono.empty())
                .thenMany(templates(companyId))
                .filter(t -> t.name().equalsIgnoreCase(cleaned))
                .next();
    }

    public Mono<Void> deleteTemplate(UUID companyId, UUID id) {
        return db.sql("DELETE FROM fleet.shift_templates WHERE id = :id AND company_id = :c")
                .bind("id", id).bind("c", companyId)
                .fetch().rowsUpdated()
                .flatMap(n -> n == 0 ? Mono.error(PlanningException.invalidTemplate("Modèle introuvable.")) : Mono.empty());
    }

    private Mono<Void> seedDefaults(UUID companyId) {
        return db.sql("""
                INSERT INTO fleet.shift_templates (id, company_id, name, start_time, end_time, sort_order)
                SELECT gen_random_uuid(), :c, t.name, t.start_time, t.end_time, t.sort_order
                FROM (VALUES ('Matin', TIME '06:00', TIME '14:00', 1), ('Journée', TIME '08:00', TIME '17:00', 2),
                             ('Après-midi', TIME '14:00', TIME '22:00', 3), ('Nuit', TIME '22:00', TIME '06:00', 4))
                     AS t(name, start_time, end_time, sort_order)
                ON CONFLICT DO NOTHING
                """)
                .bind("c", companyId)
                .fetch().rowsUpdated().then();
    }

    // ── Création en série ────────────────────────────────────────────────────

    /** Le même poste sur plusieurs jours, tout ou rien ; une fin avant le début désigne un poste de nuit. */
    @Transactional
    public Mono<List<Assignment>> bulkCreate(BulkCommand cmd) {
        List<LocalDate> days = cmd.dates().stream().distinct().sorted().toList();
        if (days.isEmpty() || days.size() > MAX_BULK_DAYS) {
            return Mono.error(PlanningException.invalidBulk("Choisissez de 1 à " + MAX_BULK_DAYS + " jours."));
        }
        if (cmd.startTime().equals(cmd.endTime())) {
            return Mono.error(PlanningException.invalidBulk("Les heures de début et de fin doivent être différentes."));
        }
        boolean overnight = cmd.endTime().isBefore(cmd.startTime());
        return Flux.fromIterable(days).concatMap(day -> {
            LocalDateTime start = day.atTime(cmd.startTime());
            LocalDateTime end = (overnight ? day.plusDays(1) : day).atTime(cmd.endTime());
            return assignments.createAssignment(new ManageAssignmentUseCase.CreateAssignmentCommand(
                            cmd.scheduleId(), cmd.fleetId(), cmd.vehicleId(), cmd.driverId(), start, end,
                            cmd.notes(), cmd.workRuleOverride(), cmd.changeReason()))
                    .onErrorMap(DomainException.class, e -> PlanningException.prefixed("Le " + day.format(DAY) + " : ", e));
        }).collectList();
    }

    // ── Duplication ──────────────────────────────────────────────────────────

    /**
     * Copie un planning sur une nouvelle période (même durée), en brouillon. Chaque poste est
     * recréé avec toutes les règles (disponibilité, conformité, temps de travail) : ceux qui ne
     * passent plus sont listés avec leur motif au lieu d'être forcés.
     */
    public Mono<DuplicateResult> duplicate(UUID scheduleId, UUID managerId, LocalDate newStart, String title) {
        return schedules.findById(scheduleId)
                .switchIfEmpty(Mono.error(PlanningException.scheduleNotFound(scheduleId)))
                .flatMap(source -> {
                    if (newStart == null) {
                        return Mono.error(PlanningException.invalidDates());
                    }
                    long shift = ChronoUnit.DAYS.between(source.getStartDate(), newStart);
                    if (shift == 0) {
                        return Mono.error(PlanningException.invalidBulk("Choisissez une autre période que celle du planning copié."));
                    }
                    String name = title != null && !title.isBlank() ? title.trim() : source.getTitle() + " (copie)";
                    Schedule copy = new Schedule(null, source.getFleetId(), managerId, name, source.getPeriodType(),
                            newStart, source.getEndDate().plusDays(shift), Schedule.Status.DRAFT, source.getNotes(), null, managerId);
                    return schedules.save(copy).flatMap(saved -> assignmentPort.findAllByScheduleId(scheduleId)
                            .filter(a -> a.getStatus() != Assignment.Status.CANCELLED && a.getStatus() != Assignment.Status.NO_SHOW)
                            .sort(java.util.Comparator.comparing(Assignment::getStartDatetime))
                            .concatMap(a -> {
                                LocalDateTime start = a.getStartDatetime().plusDays(shift);
                                return assignments.createAssignment(new ManageAssignmentUseCase.CreateAssignmentCommand(
                                                saved.getId(), a.getFleetId(), a.getVehicleId(), a.getDriverId(), start,
                                                a.getEndDatetime().plusDays(shift), a.getNotes(), null, null))
                                        .map(created -> (Object) created)
                                        .onErrorResume(e -> Mono.just(new SkippedShift(a.getDriverId(), a.getVehicleId(), start,
                                                e.getMessage() != null ? e.getMessage() : "Refusé")));
                            })
                            .collectList()
                            .map(results -> {
                                List<SkippedShift> skipped = new ArrayList<>();
                                int copied = 0;
                                for (Object r : results) {
                                    if (r instanceof SkippedShift s) skipped.add(s);
                                    else copied++;
                                }
                                return new DuplicateResult(saved, copied, skipped);
                            }));
                });
    }
}
