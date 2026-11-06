package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.OperationException;
import com.yowyob.fleet.domain.model.Incident;
import com.yowyob.fleet.domain.model.IncidentRules;
import com.yowyob.fleet.domain.ports.in.ManageExpenseUseCase;
import com.yowyob.fleet.domain.ports.in.ManageMaintenanceUseCase;
import com.yowyob.fleet.domain.ports.out.IncidentPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.IncidentCaseEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.IncidentCaseR2dbcRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Instruction d'un incident : étapes du dossier, historique, pièces, coûts réels (dépenses soumises
 * à approbation), assurance, immobilisation décidée par le gestionnaire, ordre de réparation.
 */
@Service
@RequiredArgsConstructor
public class IncidentCaseService {

    private final IncidentPersistencePort incidents;
    private final IncidentCaseR2dbcRepository cases;
    private final IncidentImpactService impact;
    private final AccessScopeService accessScope;
    private final ManageExpenseUseCase expenses;
    private final ManageMaintenanceUseCase maintenances;
    private final DatabaseClient db;
    private final IncidentNotifier notifier;

    public record CaseDetails(String locationLabel, BigDecimal estimatedCost, Boolean injuries, Integer injuriesCount,
                              Boolean thirdPartyInvolved, String thirdPartyName, String thirdPartyPlate,
                              String thirdPartyInsurer, String thirdPartyContact, String insuranceStatus, String insurerName,
                              BigDecimal refundAmount, LocalDate refundDate, String rootCause, String correctiveAction,
                              String responsibility, String policeReportNumber, String insuranceClaimNumber,
                              UUID handlerId) {
    }

    public record TransitionCommand(Incident.Status status, UUID handlerId, String note, String report, String rootCause,
                                    String correctiveAction, String responsibility) {
    }

    public record AttachmentCommand(String kind, String fileUrl, String fileName, String mimeType, Long sizeBytes) {
    }

    public record CostCommand(String nature, BigDecimal amount, String description, String receiptUrl, String receiptName,
                              String receiptMime) {
    }

    public record Attachment(UUID id, String kind, String fileUrl, String fileName, String mimeType, String uploadedBy,
                             LocalDateTime createdAt) {
    }

    /** Étape de l'historique ; {@code byDriver} : écrite par un chauffeur (message, réponse, photo). */
    public record TimelineEvent(LocalDateTime at, String type, String fromStatus, String toStatus, String actor, String note,
                                boolean byDriver) {
    }

    /** Résumé d'un incident pour les listes (gestionnaire, chauffeur, administrateur). */
    public record Summary(String locationLabel, int photoCount, int attachmentCount, String reportedByName, String handlerName,
                          UUID tripId, String tripCode, boolean tripInterrupted, LocalDateTime declaredAt,
                          LocalDateTime lastEventAt, String lastEventType, boolean infoRequestPending) {
    }

    public record Enriched(Incident incident, Summary summary) {
    }

    public record CostLine(UUID id, String type, BigDecimal amount, String description, String status, String approvalLevel,
                           String receiptUrl, LocalDateTime date, String rejectionReason, String source) {
    }

    public record CaseView(Incident incident, IncidentCaseEntity details, String tripCode, String handlerName, String reportedByName,
                           String vehicleStatus, List<Attachment> attachments, List<TimelineEvent> timeline, List<CostLine> costs,
                           BigDecimal approvedCosts, BigDecimal pendingCosts, BigDecimal netCost, List<String> closureGaps,
                           List<Map<String, Object>> handlers, String pendingInfoRequest) {
    }

    // ── Déclaration (appelée à la création de l'incident) ────────────────────

    public Mono<IncidentCaseEntity> open(Incident incident, UUID reporterId, CaseDetails details) {
        return resolve(reporterId).flatMap(reporter -> tripInProgress(incident.getVehicleId())
                .flatMap(tripId -> {
                    IncidentCaseEntity c = new IncidentCaseEntity();
                    c.setIncidentId(incident.getId());
                    c.setNew(true);
                    c.setReportedById(reporter.orElse(null));
                    c.setTripId(tripId.orElse(null));
                    apply(c, details);
                    return cases.save(c);
                })
                .flatMap(saved -> event(incident.getId(), "DECLARED", null, "REPORTED", reporter.orElse(null), null).thenReturn(saved)));
    }

    private Mono<Optional<UUID>> tripInProgress(UUID vehicleId) {
        return db.sql("SELECT id FROM fleet.trips WHERE vehicle_id = :v AND deleted_at IS NULL AND status IN ('DEPARTED', 'ARRIVED') LIMIT 1")
                .bind("v", vehicleId)
                .map(r -> r.get("id", UUID.class))
                .one()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    private Mono<Optional<UUID>> resolve(UUID userId) {
        return accessScope.resolveLocalUserId(userId).map(Optional::of).defaultIfEmpty(Optional.empty());
    }

    // ── Lecture ──────────────────────────────────────────────────────────────

    public Mono<CaseView> view(UUID incidentId) {
        return requireIncident(incidentId).flatMap(incident -> requireCase(incident).flatMap(c -> {
            Mono<List<Attachment>> attachments = db.sql("""
                    SELECT a.id, a.kind, a.file_url, a.file_name, a.mime_type, a.created_at,
                           NULLIF(TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), '') AS who
                    FROM fleet.incident_attachments a LEFT JOIN fleet.users u ON u.id = a.uploaded_by
                    WHERE a.incident_id = :id ORDER BY a.created_at
                    """).bind("id", incidentId)
                    .map(r -> new Attachment(r.get("id", UUID.class), r.get("kind", String.class), r.get("file_url", String.class),
                            r.get("file_name", String.class), r.get("mime_type", String.class), r.get("who", String.class),
                            r.get("created_at", LocalDateTime.class)))
                    .all().collectList();
            Mono<List<TimelineEvent>> timeline = timeline(incidentId);
            Mono<List<CostLine>> costs = costs(incidentId);
            Mono<Map<String, Object>> labels = db.sql("""
                    SELECT (SELECT trip_code FROM fleet.trips WHERE id = :trip) AS trip_code,
                           (SELECT NULLIF(TRIM(COALESCE(first_name, '') || ' ' || COALESCE(last_name, '')), '') FROM fleet.users WHERE id = :handler) AS handler,
                           (SELECT NULLIF(TRIM(COALESCE(first_name, '') || ' ' || COALESCE(last_name, '')), '') FROM fleet.users WHERE id = :reporter) AS reporter,
                           (SELECT status FROM fleet.vehicles WHERE id = :vehicle) AS vehicle_status
                    """)
                    .bind("trip", uuidOrZero(c.getTripId()))
                    .bind("handler", uuidOrZero(c.getHandlerId()))
                    .bind("reporter", uuidOrZero(c.getReportedById()))
                    .bind("vehicle", incident.getVehicleId())
                    .fetch().one();
            Mono<List<Map<String, Object>>> handlers = db.sql("""
                    SELECT u.id, TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')) AS name
                    FROM fleet.vehicles v JOIN fleet.fleets f ON f.id = v.fleet_id
                    JOIN fleet.users u ON u.id IN (f.manager_id, f.company_id)
                    WHERE v.id = :vehicle
                    """).bind("vehicle", incident.getVehicleId())
                    .fetch().all().collectList();
            return Mono.zip(attachments, timeline, costs, labels, handlers).map(t -> {
                List<CostLine> lines = t.getT3();
                BigDecimal approved = sum(lines, "APPROVED");
                BigDecimal pending = sum(lines, "PENDING");
                BigDecimal refund = c.getRefundAmount() != null ? c.getRefundAmount() : BigDecimal.ZERO;
                return new CaseView(incident, c, (String) t.getT4().get("trip_code"), (String) t.getT4().get("handler"),
                        (String) t.getT4().get("reporter"), (String) t.getT4().get("vehicle_status"), t.getT1(), t.getT2(), lines,
                        approved, pending, approved.subtract(refund), closureGaps(incident, c, t.getT1().size(), lines), t.getT5(),
                        pendingInfoRequest(t.getT2()));
            });
        }));
    }

    private Mono<List<TimelineEvent>> timeline(UUID incidentId) {
        return db.sql("""
                SELECT e.created_at, e.event_type, e.from_status, e.to_status, e.note,
                       NULLIF(TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), '') AS who,
                       EXISTS (SELECT 1 FROM fleet.drivers d WHERE d.user_id = e.actor_id) AS by_driver
                FROM fleet.incident_events e LEFT JOIN fleet.users u ON u.id = e.actor_id
                WHERE e.incident_id = :id ORDER BY e.created_at, e.id
                """).bind("id", incidentId)
                .map(r -> new TimelineEvent(r.get("created_at", LocalDateTime.class), r.get("event_type", String.class),
                        r.get("from_status", String.class), r.get("to_status", String.class), r.get("who", String.class),
                        r.get("note", String.class), Boolean.TRUE.equals(r.get("by_driver", Boolean.class))))
                .all().collectList();
    }

    /**
     * Demande d'information du gestionnaire encore sans réponse du chauffeur (son texte), ou null.
     * Un message ou une pièce ajoutés par le chauffeur après la demande y répondent.
     */
    static String pendingInfoRequest(List<TimelineEvent> timeline) {
        String pending = null;
        for (TimelineEvent e : timeline) {
            if (INFO_REQUESTED.equals(e.type())) {
                pending = e.note() != null ? e.note() : "";
            } else if (pending != null && (INFO_PROVIDED.equals(e.type()) || DRIVER_COMMENT.equals(e.type())
                    || (ATTACHMENT_ADDED.equals(e.type()) && e.byDriver()))) {
                pending = null;
            }
        }
        return pending;
    }

    static final String COMMENT = "COMMENT";
    static final String INFO_REQUESTED = "INFO_REQUESTED";
    static final String DRIVER_COMMENT = "DRIVER_COMMENT";
    static final String INFO_PROVIDED = "INFO_PROVIDED";
    static final String ATTACHMENT_ADDED = "ATTACHMENT_ADDED";

    /** Résumés pour les listes, en une requête ; les plus récemment déclarés d'abord. */
    public Flux<Enriched> enrich(Flux<Incident> source) {
        return source.collectList().flatMapMany(list -> summaries(list.stream().map(Incident::getId).toList())
                .flatMapMany(map -> {
                    List<Enriched> out = new ArrayList<>();
                    for (Incident i : list) out.add(new Enriched(i, map.get(i.getId())));
                    out.sort(Comparator.comparing(IncidentCaseService::declaredAt, Comparator.nullsLast(Comparator.reverseOrder())));
                    return Flux.fromIterable(out);
                }));
    }

    private static LocalDateTime declaredAt(Enriched e) {
        return e.summary() != null && e.summary().declaredAt() != null ? e.summary().declaredAt() : e.incident().getIncidentDateTime();
    }

    public Mono<Map<UUID, Summary>> summaries(Collection<UUID> ids) {
        if (ids.isEmpty()) return Mono.just(Map.of());
        return db.sql("""
                SELECT i.id, c.location_label, c.trip_id, t.trip_code,
                       (t.interrupted_at IS NOT NULL AND t.resumed_at IS NULL AND t.interruption_incident_id = i.id) AS trip_interrupted,
                       NULLIF(TRIM(COALESCE(ru.first_name, '') || ' ' || COALESCE(ru.last_name, '')), '') AS reporter_name,
                       NULLIF(TRIM(COALESCE(hu.first_name, '') || ' ' || COALESCE(hu.last_name, '')), '') AS handler_name,
                       (SELECT COUNT(*) FROM fleet.incident_attachments a WHERE a.incident_id = i.id AND a.kind = 'PHOTO') AS photo_count,
                       (SELECT COUNT(*) FROM fleet.incident_attachments a WHERE a.incident_id = i.id) AS attachment_count,
                       (SELECT MIN(e.created_at) FROM fleet.incident_events e WHERE e.incident_id = i.id AND e.event_type = 'DECLARED') AS declared_at,
                       le.created_at AS last_event_at, le.event_type AS last_event_type,
                       EXISTS (SELECT 1 FROM fleet.incident_events q WHERE q.incident_id = i.id AND q.event_type = 'INFO_REQUESTED'
                               AND NOT EXISTS (SELECT 1 FROM fleet.incident_events r WHERE r.incident_id = i.id AND r.created_at >= q.created_at
                                       AND (r.event_type IN ('INFO_PROVIDED', 'DRIVER_COMMENT')
                                            OR (r.event_type = 'ATTACHMENT_ADDED' AND EXISTS (SELECT 1 FROM fleet.drivers d WHERE d.user_id = r.actor_id)))))
                           AS info_pending
                FROM fleet.incidents i
                LEFT JOIN fleet.incident_cases c ON c.incident_id = i.id
                LEFT JOIN fleet.trips t ON t.id = c.trip_id
                LEFT JOIN fleet.users ru ON ru.id = c.reported_by_id
                LEFT JOIN fleet.users hu ON hu.id = c.handler_id
                LEFT JOIN LATERAL (SELECT e.created_at, e.event_type FROM fleet.incident_events e WHERE e.incident_id = i.id
                                   ORDER BY e.created_at DESC LIMIT 1) le ON TRUE
                WHERE i.id IN (:ids)
                """).bind("ids", List.copyOf(ids))
                .map(r -> Map.entry(r.get("id", UUID.class), new Summary(r.get("location_label", String.class),
                        count(r.get("photo_count", Long.class)), count(r.get("attachment_count", Long.class)),
                        r.get("reporter_name", String.class), r.get("handler_name", String.class), r.get("trip_id", UUID.class),
                        r.get("trip_code", String.class), Boolean.TRUE.equals(r.get("trip_interrupted", Boolean.class)),
                        r.get("declared_at", LocalDateTime.class), r.get("last_event_at", LocalDateTime.class),
                        r.get("last_event_type", String.class), Boolean.TRUE.equals(r.get("info_pending", Boolean.class)))))
                .all()
                .collectMap(Map.Entry::getKey, Map.Entry::getValue, HashMap::new)
                .map(m -> (Map<UUID, Summary>) m)
                .onErrorResume(e -> Mono.just(Map.of()));
    }

    private static int count(Long n) {
        return n != null ? n.intValue() : 0;
    }

    private static UUID uuidOrZero(UUID id) {
        return id != null ? id : new UUID(0, 0);
    }

    private static BigDecimal sum(List<CostLine> lines, String status) {
        return lines.stream().filter(l -> status.equals(l.status())).map(CostLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Mono<List<CostLine>> costs(UUID incidentId) {
        return db.sql("""
                SELECT id, expense_type, amount, description, status, approval_level, receipt_url, expense_date,
                       rejection_reason, source_type
                FROM fleet.expenses WHERE source_id = :id ORDER BY expense_date
                """).bind("id", incidentId)
                .map(r -> new CostLine(r.get("id", UUID.class), r.get("expense_type", String.class), r.get("amount", BigDecimal.class),
                        r.get("description", String.class), r.get("status", String.class), r.get("approval_level", String.class),
                        r.get("receipt_url", String.class), r.get("expense_date", LocalDateTime.class),
                        r.get("rejection_reason", String.class), r.get("source_type", String.class)))
                .all().collectList();
    }

    private static List<String> closureGaps(Incident i, IncidentCaseEntity c, int attachmentCount, List<CostLine> lines) {
        return IncidentRules.closureGaps(new IncidentRules.CaseFacts(i.getType(), c.isThirdPartyInvolved(), c.isInjuries(),
                c.getInsuranceStatus(), i.getPoliceReportNumber(), i.getInsuranceClaimNumber(), c.getRootCause(),
                c.getCorrectiveAction(), c.getResponsibility(), attachmentCount, c.getMaintenanceId() != null,
                (int) lines.stream().filter(l -> "APPROVED".equals(l.status())).count(),
                (int) lines.stream().filter(l -> "PENDING".equals(l.status())).count()));
    }

    /** Incidents où la personne est le chauffeur concerné ou le déclarant. */
    public Flux<Incident> mine(UUID actorId) {
        return accessScope.resolveLocalUserId(actorId).flatMapMany(localId -> db.sql("""
                        SELECT i.id FROM fleet.incidents i LEFT JOIN fleet.incident_cases c ON c.incident_id = i.id
                        WHERE i.driver_id = :u OR c.reported_by_id = :u ORDER BY i.incident_date_time DESC
                        """).bind("u", localId)
                .map(r -> r.get("id", UUID.class)).all()
                .concatMap(incidents::findById));
    }

    // ── Instruction ──────────────────────────────────────────────────────────

    @Transactional
    public Mono<CaseView> update(UUID actorId, UUID incidentId, CaseDetails details) {
        return editable(incidentId).flatMap(incident -> requireCase(incident).flatMap(c -> {
            UUID previousHandler = c.getHandlerId();
            apply(c, details);
            c.setNew(false);
            if (details.policeReportNumber() != null) incident.setPoliceReportNumber(details.policeReportNumber());
            if (details.insuranceClaimNumber() != null) incident.setInsuranceClaimNumber(details.insuranceClaimNumber());
            boolean reassigned = c.getHandlerId() != null && !c.getHandlerId().equals(previousHandler);
            return cases.save(c).then(incidents.save(incident))
                    .then(resolve(actorId))
                    .flatMap(actor -> event(incidentId, "UPDATED", null, null, actor.orElse(null), null)
                            .then(reassigned ? notifier.assigned(incident, c.getHandlerId(), actor.orElse(null)) : Mono.<Void>empty()));
        })).then(Mono.defer(() -> view(incidentId)));
    }

    /** Une étape à la fois : sauter une étape ou clôturer un dossier incomplet est refusé. */
    @Transactional
    public Mono<CaseView> transition(UUID actorId, UUID incidentId, TransitionCommand cmd) {
        return requireIncident(incidentId).flatMap(incident -> requireCase(incident).flatMap(c -> resolve(actorId).flatMap(actor -> {
            Incident.Status from = incident.getStatus();
            Incident.Status to = cmd.status();
            if (to == null || !IncidentRules.canTransition(from, to)) {
                return Mono.<Void>error(OperationException.incidentInvalidTransition(String.valueOf(from), String.valueOf(to)));
            }
            LocalDateTime now = LocalDateTime.now();
            if (cmd.rootCause() != null) c.setRootCause(cmd.rootCause());
            if (cmd.correctiveAction() != null) c.setCorrectiveAction(cmd.correctiveAction());
            if (cmd.responsibility() != null) c.setResponsibility(cmd.responsibility());
            c.setNew(false);

            Mono<Void> guard = Mono.empty();
            UUID previousHandler = c.getHandlerId();
            switch (to) {
                case ACKNOWLEDGED -> {
                    c.setHandlerId(cmd.handlerId() != null ? cmd.handlerId() : actor.orElse(null));
                    c.setAcknowledgedAt(now);
                    incident.updateStatus(to);
                }
                case RESOLVED -> incident.resolve(cmd.report() != null ? cmd.report() : incident.getReport());
                case CLOSED -> {
                    guard = Mono.zip(attachmentCount(incidentId), costs(incidentId)).flatMap(t -> {
                        List<String> gaps = closureGaps(incident, c, t.getT1(), t.getT2());
                        return gaps.isEmpty()
                                ? Mono.<Void>empty()
                                : Mono.<Void>error(OperationException.incidentCaseIncomplete(String.join(", ", gaps)));
                    });
                    c.setClosedAt(now);
                    incident.close();
                }
                case CANCELLED -> {
                    if (cmd.note() == null || cmd.note().isBlank()) {
                        return Mono.<Void>error(OperationException.incidentReasonRequired());
                    }
                    c.setCancelledAt(now);
                    c.setCancelReason(cmd.note().trim());
                    incident.cancel();
                }
                default -> incident.updateStatus(to);
            }
            return guard
                    .then(Mono.defer(() -> cases.save(c)))
                    .then(Mono.defer(() -> incidents.save(incident)))
                    .flatMap(saved -> impact.applyImpact(saved, actor.orElse(null)))
                    .then(event(incidentId, "STATUS_CHANGED", from.name(), to.name(), actor.orElse(null), cmd.note()))
                    .then(Mono.defer(() -> notifier.statusChanged(incident, from, to, actor.orElse(null),
                            to == Incident.Status.RESOLVED && cmd.note() == null ? cmd.report() : cmd.note())))
                    .then(Mono.defer(() -> c.getHandlerId() != null && !c.getHandlerId().equals(previousHandler)
                            ? notifier.assigned(incident, c.getHandlerId(), actor.orElse(null))
                            : Mono.<Void>empty()));
        }))).then(Mono.defer(() -> view(incidentId)));
    }

    private Mono<Integer> attachmentCount(UUID incidentId) {
        return db.sql("SELECT COUNT(*) AS n FROM fleet.incident_attachments WHERE incident_id = :id")
                .bind("id", incidentId)
                .map(r -> r.get("n", Long.class).intValue())
                .one();
    }

    /** Le gestionnaire décide : immobiliser un véhicule roulant, ou libérer un véhicule immobilisé. */
    @Transactional
    public Mono<CaseView> setImmobilization(UUID actorId, UUID incidentId, boolean immobilized, String reason) {
        if (reason == null || reason.isBlank()) {
            return Mono.error(OperationException.incidentReasonRequired());
        }
        return editable(incidentId).flatMap(incident -> resolve(actorId).flatMap(actor -> {
            incident.setImmobilizationOverride(immobilized);
            return incidents.save(incident)
                    .flatMap(saved -> impact.applyImpact(saved, actor.orElse(null)))
                    .then(event(incidentId, immobilized ? "IMMOBILIZED" : "RELEASED", null, null, actor.orElse(null), reason.trim()))
                    .then(Mono.defer(() -> notifier.immobilization(incident, immobilized, actor.orElse(null), reason.trim())));
        })).then(Mono.defer(() -> view(incidentId)));
    }

    @Transactional
    public Mono<CaseView> addAttachment(UUID actorId, UUID incidentId, AttachmentCommand cmd) {
        return addAttachment(actorId, incidentId, cmd, false);
    }

    /** Pièce jointe ; {@code fromDriver} : ajoutée par le chauffeur (le bureau est prévenu), sinon le chauffeur l'est. */
    @Transactional
    public Mono<CaseView> addAttachment(UUID actorId, UUID incidentId, AttachmentCommand cmd, boolean fromDriver) {
        if (cmd.fileUrl() == null || !cmd.fileUrl().startsWith("/api/v1/files/")) {
            return Mono.error(OperationException.incidentFileRequired());
        }
        String kind = List.of("PHOTO", "REPORT", "INVOICE", "OTHER").contains(cmd.kind()) ? cmd.kind() : "PHOTO";
        return editable(incidentId).flatMap(incident -> resolve(actorId).flatMap(actor -> {
            var spec = db.sql("""
                    INSERT INTO fleet.incident_attachments (id, incident_id, kind, file_url, file_name, mime_type, size_bytes, uploaded_by, created_at)
                    VALUES (gen_random_uuid(), :incident, :kind, :url, :name, :mime, :size, :by, :now)
                    """)
                    .bind("incident", incidentId).bind("kind", kind).bind("url", cmd.fileUrl())
                    .bind("now", LocalDateTime.now());
            spec = cmd.fileName() != null ? spec.bind("name", cmd.fileName()) : spec.bindNull("name", String.class);
            spec = cmd.mimeType() != null ? spec.bind("mime", cmd.mimeType()) : spec.bindNull("mime", String.class);
            spec = cmd.sizeBytes() != null ? spec.bind("size", cmd.sizeBytes()) : spec.bindNull("size", Long.class);
            spec = actor.isPresent() ? spec.bind("by", actor.get()) : spec.bindNull("by", UUID.class);
            return spec.fetch().rowsUpdated()
                    .then(event(incidentId, ATTACHMENT_ADDED, null, null, actor.orElse(null), cmd.fileName()))
                    .then(Mono.defer(() -> notifier.attachmentAdded(incident, actor.orElse(null), fromDriver, cmd.fileName())));
        })).then(Mono.defer(() -> view(incidentId)));
    }

    /**
     * Échange sur le dossier. Le gestionnaire écrit au chauffeur (simple message ou demande d'information,
     * {@code requestInfo}) ; le chauffeur répond ou ajoute une précision. Chaque message entre dans
     * l'historique et prévient l'autre côté. Dossier clôturé ou annulé : plus d'échange.
     */
    @Transactional
    public Mono<CaseView> comment(UUID actorId, UUID incidentId, boolean fromDriver, String message, boolean requestInfo) {
        if (message == null || message.isBlank()) {
            return Mono.error(OperationException.incidentMessageRequired());
        }
        String text = message.trim().length() > 2000 ? message.trim().substring(0, 2000) : message.trim();
        return editable(incidentId).flatMap(incident -> resolve(actorId).flatMap(actor -> timeline(incidentId).flatMap(events -> {
            boolean answers = pendingInfoRequest(events) != null;
            String type = fromDriver ? (answers ? INFO_PROVIDED : DRIVER_COMMENT) : (requestInfo ? INFO_REQUESTED : COMMENT);
            return event(incidentId, type, null, null, actor.orElse(null), text)
                    .then(Mono.defer(() -> fromDriver
                            ? notifier.driverMessage(incident, actor.orElse(null), answers, text)
                            : notifier.staffMessage(incident, actor.orElse(null), requestInfo, text)));
        }))).then(Mono.defer(() -> view(incidentId)));
    }

    /** Coût réel : une dépense en attente de validation, avec justificatif. Le budget n'est touché qu'à l'approbation. */
    @Transactional
    public Mono<CaseView> addCost(UUID actorId, UUID incidentId, CostCommand cmd) {
        return editable(incidentId).flatMap(incident -> expenses.createIncidentCost(new ManageExpenseUseCase.IncidentCostCommand(
                        incidentId, incident.getVehicleId(), incident.getDriverId(), cmd.nature(), cmd.amount(), cmd.description(),
                        cmd.receiptUrl(), cmd.receiptName(), cmd.receiptMime(), actorId))
                .then(resolve(actorId))
                .flatMap(actor -> event(incidentId, "COST_ADDED", null, null, actor.orElse(null), cmd.description())))
                .then(Mono.defer(() -> view(incidentId)));
    }

    /** Panne : ouvre l'ordre de réparation dans les maintenances du véhicule et l'attache au dossier. */
    @Transactional
    public Mono<CaseView> createRepairOrder(UUID actorId, UUID incidentId, String subject) {
        return editable(incidentId).flatMap(incident -> requireCase(incident).flatMap(c -> {
            if (c.getMaintenanceId() != null) {
                return Mono.<Void>empty();
            }
            String title = subject != null && !subject.isBlank() ? subject.trim() : "Réparation : " + incident.getType().label();
            return maintenances.createMaintenance(new ManageMaintenanceUseCase.CreateMaintenanceCommand(
                            title, null, incident.getDescription(), null, null, c.getLocationLabel(), incident.getVehicleId(), incident.getDriverId()))
                    .flatMap(m -> {
                        c.setMaintenanceId(m.getId());
                        c.setNew(false);
                        return cases.save(c);
                    })
                    .then(resolve(actorId))
                    .flatMap(actor -> event(incidentId, "REPAIR_ORDER", null, null, actor.orElse(null), title)
                            .then(Mono.defer(() -> notifier.repairOrder(incident, actor.orElse(null), c.getMaintenanceId(), title))));
        })).then(Mono.defer(() -> view(incidentId)));
    }

    // ── Règles communes ──────────────────────────────────────────────────────

    private Mono<Incident> requireIncident(UUID id) {
        return incidents.findById(id).switchIfEmpty(Mono.error(OperationException.incidentNotFound(id)));
    }

    /** Dossier clôturé ou annulé : figé. */
    private Mono<Incident> editable(UUID id) {
        return requireIncident(id).flatMap(i -> i.getStatus() == Incident.Status.CLOSED || i.getStatus() == Incident.Status.CANCELLED
                ? Mono.<Incident>error(OperationException.incidentAlreadyClosed(id))
                : Mono.just(i));
    }

    private Mono<IncidentCaseEntity> requireCase(Incident incident) {
        return cases.findById(incident.getId()).switchIfEmpty(Mono.defer(() -> {
            IncidentCaseEntity c = new IncidentCaseEntity();
            c.setIncidentId(incident.getId());
            c.setNew(true);
            return cases.save(c);
        }));
    }

    private static void apply(IncidentCaseEntity c, CaseDetails d) {
        if (d == null) return;
        if (d.locationLabel() != null) c.setLocationLabel(d.locationLabel());
        if (d.estimatedCost() != null) c.setEstimatedCost(d.estimatedCost());
        if (d.injuries() != null) c.setInjuries(d.injuries());
        if (d.injuriesCount() != null) c.setInjuriesCount(d.injuriesCount());
        if (d.thirdPartyInvolved() != null) c.setThirdPartyInvolved(d.thirdPartyInvolved());
        if (d.thirdPartyName() != null) c.setThirdPartyName(d.thirdPartyName());
        if (d.thirdPartyPlate() != null) c.setThirdPartyPlate(d.thirdPartyPlate());
        if (d.thirdPartyInsurer() != null) c.setThirdPartyInsurer(d.thirdPartyInsurer());
        if (d.thirdPartyContact() != null) c.setThirdPartyContact(d.thirdPartyContact());
        if (d.insuranceStatus() != null) c.setInsuranceStatus(d.insuranceStatus());
        if (d.insurerName() != null) c.setInsurerName(d.insurerName());
        if (d.refundAmount() != null) c.setRefundAmount(d.refundAmount());
        if (d.refundDate() != null) c.setRefundDate(d.refundDate());
        if (d.rootCause() != null) c.setRootCause(d.rootCause());
        if (d.correctiveAction() != null) c.setCorrectiveAction(d.correctiveAction());
        if (d.responsibility() != null) c.setResponsibility(d.responsibility());
        if (d.handlerId() != null) c.setHandlerId(d.handlerId());
    }

    private Mono<Void> event(UUID incidentId, String type, String from, String to, UUID actor, String note) {
        var spec = db.sql("""
                INSERT INTO fleet.incident_events (id, incident_id, event_type, from_status, to_status, actor_id, note, created_at)
                VALUES (gen_random_uuid(), :id, :type, :from, :to, :actor, :note, :now)
                """).bind("id", incidentId).bind("type", type).bind("now", LocalDateTime.now());
        spec = from != null ? spec.bind("from", from) : spec.bindNull("from", String.class);
        spec = to != null ? spec.bind("to", to) : spec.bindNull("to", String.class);
        spec = actor != null ? spec.bind("actor", actor) : spec.bindNull("actor", UUID.class);
        spec = note != null ? spec.bind("note", note) : spec.bindNull("note", String.class);
        return spec.fetch().rowsUpdated().then();
    }
}
