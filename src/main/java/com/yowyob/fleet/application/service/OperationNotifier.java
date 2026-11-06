package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.Expense;
import com.yowyob.fleet.domain.model.FuelRecharge;
import com.yowyob.fleet.domain.model.Maintenance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notifications in-app des opérations terrain (plein, maintenance, dépenses), entre chauffeur,
 * gestionnaire et administrateur. Codes d'événement :
 * <ul>
 *   <li>FUEL_DECLARED (chauffeur → gestionnaire), FUEL_RECORDED (gestionnaire → chauffeur) ;</li>
 *   <li>MAINTENANCE_REQUESTED (chauffeur → gestionnaire), MAINTENANCE_RECORDED, MAINTENANCE_PLANNED,
 *       MAINTENANCE_STARTED, MAINTENANCE_COMPLETED, MAINTENANCE_CANCELLED (gestionnaire → chauffeur) ;</li>
 *   <li>EXPENSE_SUBMITTED (chauffeur → gestionnaire), EXPENSE_ADMIN_APPROVAL (→ administrateur),
 *       EXPENSE_APPROVED / EXPENSE_REJECTED (→ chauffeur concerné, auteur, gestionnaire si décision admin).</li>
 * </ul>
 * L'auteur de l'action n'est jamais notifié (AppNotifier) ; aucune erreur ne remonte à l'action métier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationNotifier {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    private final AppNotifier notifier;
    private final OperationLookup lookup;

    // ── Plein ────────────────────────────────────────────────────────────────

    /** Plein déclaré par le chauffeur → gestionnaire ; saisi par le gestionnaire → chauffeur concerné. */
    public Mono<Void> fuelCreated(FuelRecharge r, UUID actorId) {
        return safe(Mono.zip(local(actorId), lookup.vehicle(r.getVehicleId()),
                        lookup.autoExpense(r.getId()).map(e -> List.of(e)).defaultIfEmpty(List.of()))
                .flatMap(t -> {
                    UUID actor = t.getT1().orElse(null);
                    OperationLookup.VehicleRef v = t.getT2();
                    UUID expenseId = t.getT3().isEmpty() ? null : t.getT3().get(0).id();
                    boolean pending = !t.getT3().isEmpty() && "PENDING".equals(t.getT3().get(0).status());
                    Map<String, Object> refs = AppNotifier.refs("fuelRechargeId", r.getId(), "expenseId", expenseId,
                            "vehicleId", r.getVehicleId(), "driverId", r.getDriverId());
                    boolean byDriver = actor != null && actor.equals(r.getDriverId());
                    if (byDriver) {
                        return lookup.fullName(r.getDriverId()).flatMap(name -> notifier.toFleetManager(v.fleetId(), actor,
                                "Plein déclaré",
                                fuelDeclaredMessage(name, r, v.plate(), pending),
                                AppNotifier.Level.INFO, "FUEL_DECLARED", refs));
                    }
                    Flux<UUID> recipients = r.getDriverId() != null ? Flux.just(r.getDriverId()) : lookup.vehicleDrivers(r.getVehicleId());
                    return recipients.collectList().flatMap(ids -> notifier.toUsers(ids, actor,
                            "Plein enregistré",
                            "Votre gestionnaire a enregistré un plein de " + liters(r.getQuantity()) + " L ("
                                    + amount(r.getPrice()) + ") pour " + plate(v.plate()) + ".",
                            AppNotifier.Level.INFO, "FUEL_RECORDED", refs));
                }), "FUEL");
    }

    static String fuelDeclaredMessage(String driverName, FuelRecharge r, String plate, boolean withExpense) {
        StringBuilder text = new StringBuilder();
        text.append(driverName == null || driverName.isBlank() ? "Un chauffeur" : driverName)
                .append(" a déclaré un plein de ").append(liters(r.getQuantity())).append(" L (")
                .append(amount(r.getPrice())).append(") pour ").append(plate(plate));
        String station = r.getStationLabel() != null ? r.getStationLabel()
                : r.getStationName() != null ? r.getStationName().name() : null;
        if (station != null) text.append(", station ").append(station);
        if (r.getOdometerKm() != null) text.append(", compteur ").append(liters(r.getOdometerKm())).append(" km");
        text.append('.');
        if (withExpense) text.append(" Dépense en attente de validation.");
        return text.toString();
    }

    // ── Maintenance ──────────────────────────────────────────────────────────

    /** Nouvelle maintenance : demande du chauffeur → gestionnaire ; saisie du gestionnaire → chauffeurs du véhicule. */
    public Mono<Void> maintenanceCreated(Maintenance m, UUID actorId) {
        return safe(Mono.zip(local(actorId), lookup.vehicle(m.getVehicleId())).flatMap(t -> {
            UUID actor = t.getT1().orElse(null);
            OperationLookup.VehicleRef v = t.getT2();
            Map<String, Object> refs = refs(m);
            if (m.getStatus() == Maintenance.Status.REQUESTED) {
                // Ordre de réparation ouvert par le gestionnaire (dossier d'incident) : rien à signaler.
                UUID requester = m.getRequestedBy();
                if (requester == null) return Mono.<Void>empty();
                return lookup.fullName(requester).flatMap(name -> notifier.toFleetManager(v.fleetId(), actor,
                        m.isUrgent() ? "Maintenance urgente demandée" : "Maintenance demandée",
                        maintenanceRequestedMessage(name, m, v.plate()),
                        m.isUrgent() ? AppNotifier.Level.WARNING : AppNotifier.Level.INFO,
                        "MAINTENANCE_REQUESTED", refs));
            }
            String event = switch (m.getStatus()) {
                case PLANNED -> "MAINTENANCE_PLANNED";
                case IN_PROGRESS -> "MAINTENANCE_STARTED";
                default -> "MAINTENANCE_RECORDED";
            };
            return recipients(m).flatMap(ids -> notifier.toUsers(ids, actor, maintenanceTitle(event),
                    maintenanceMessage(event, m, v.plate()), levelOf(event), event, refs));
        }), "MAINTENANCE");
    }

    /** Avancement décidé par le gestionnaire → demandeur et chauffeurs du véhicule. */
    public Mono<Void> maintenanceTransition(Maintenance m, UUID actorId) {
        String event = switch (m.getStatus()) {
            case PLANNED -> "MAINTENANCE_PLANNED";
            case IN_PROGRESS -> "MAINTENANCE_STARTED";
            case COMPLETED -> "MAINTENANCE_COMPLETED";
            case CANCELLED -> "MAINTENANCE_CANCELLED";
            default -> null;
        };
        if (event == null) return Mono.empty();
        return safe(Mono.zip(local(actorId), lookup.vehicle(m.getVehicleId()), recipients(m)).flatMap(t ->
                notifier.toUsers(t.getT3(), t.getT1().orElse(null), maintenanceTitle(event),
                        maintenanceMessage(event, m, t.getT2().plate()), levelOf(event), event, refs(m))), "MAINTENANCE");
    }

    static String maintenanceRequestedMessage(String driverName, Maintenance m, String plate) {
        StringBuilder text = new StringBuilder();
        text.append(driverName == null || driverName.isBlank() ? "Un chauffeur" : driverName)
                .append(" signale un besoin de maintenance");
        if (m.getMaintenanceType() != null) text.append(" (").append(typeLabel(m.getMaintenanceType())).append(')');
        text.append(" sur ").append(plate(plate));
        if (m.getReport() != null && !m.getReport().isBlank()) text.append(" : ").append(m.getReport().trim());
        text.append('.');
        if (m.isUrgent()) text.append(" Demande urgente.");
        return text.toString();
    }

    static String maintenanceTitle(String event) {
        return switch (event) {
            case "MAINTENANCE_PLANNED" -> "Maintenance planifiée";
            case "MAINTENANCE_STARTED" -> "Véhicule à l'atelier";
            case "MAINTENANCE_COMPLETED" -> "Maintenance terminée";
            case "MAINTENANCE_CANCELLED" -> "Maintenance annulée";
            default -> "Maintenance enregistrée";
        };
    }

    static String maintenanceMessage(String event, Maintenance m, String plate) {
        String subject = "« " + subjectLabel(m) + " » sur " + plate(plate);
        String garage = m.getLocationName() != null && !m.getLocationName().isBlank() ? " (" + m.getLocationName().trim() + ")" : "";
        return switch (event) {
            case "MAINTENANCE_PLANNED" -> "La maintenance " + subject + " est planifiée"
                    + (m.getPlannedAt() != null ? " le " + m.getPlannedAt().format(DATE_TIME) : "") + garage + ".";
            case "MAINTENANCE_STARTED" -> "La maintenance " + subject + " a commencé" + garage
                    + " : le véhicule est indisponible jusqu'à la fin de l'intervention.";
            case "MAINTENANCE_COMPLETED" -> "La maintenance " + subject + " est terminée.";
            case "MAINTENANCE_CANCELLED" -> "La maintenance " + subject + " est annulée"
                    + (m.getCancelReason() != null ? " : " + m.getCancelReason() : "") + ".";
            default -> "Votre gestionnaire a enregistré la maintenance " + subject + garage + ".";
        };
    }

    private static AppNotifier.Level levelOf(String event) {
        return switch (event) {
            case "MAINTENANCE_STARTED", "MAINTENANCE_CANCELLED" -> AppNotifier.Level.WARNING;
            case "MAINTENANCE_COMPLETED" -> AppNotifier.Level.SUCCESS;
            default -> AppNotifier.Level.INFO;
        };
    }

    private Mono<List<UUID>> recipients(Maintenance m) {
        List<UUID> base = new ArrayList<>();
        if (m.getRequestedBy() != null) base.add(m.getRequestedBy());
        if (m.getDriverId() != null) base.add(m.getDriverId());
        return lookup.vehicleDrivers(m.getVehicleId()).collectList().map(drivers -> {
            List<UUID> all = new ArrayList<>(base);
            all.addAll(drivers);
            return all;
        });
    }

    private static Map<String, Object> refs(Maintenance m) {
        return AppNotifier.refs("maintenanceId", m.getId(), "vehicleId", m.getVehicleId(), "driverId", m.getDriverId());
    }

    // ── Dépenses ─────────────────────────────────────────────────────────────

    /** Dépense saisie par un chauffeur → gestionnaire ; de niveau administrateur → administrateur. */
    public Mono<Void> expenseCreated(Expense e, UUID actorId) {
        return safe(Mono.zip(local(actorId), lookup.vehicle(e.getVehicleId())).flatMap(t -> {
            UUID actor = t.getT1().orElse(null);
            OperationLookup.VehicleRef v = t.getT2();
            Map<String, Object> refs = expenseRefs(e);
            Mono<Void> toManager = e.getSourceType() == Expense.SourceType.MANUAL && actor != null && actor.equals(e.getDriverId())
                    ? notifier.toUser(e.getManagerId(), actor, "Dépense à valider",
                    (e.getDriverFullName() != null ? e.getDriverFullName() : "Un chauffeur") + " a déclaré une dépense de "
                            + amount(e.getAmount()) + " pour " + plate(v.plate()) + describe(e) + ".",
                    AppNotifier.Level.INFO, "EXPENSE_SUBMITTED", refs)
                    : Mono.empty();
            Mono<Void> toAdmin = e.getApprovalLevel() == Expense.ApprovalLevel.ADMIN && e.getStatus() == Expense.ExpenseStatus.PENDING
                    ? adminApproval(e, actor, v, null)
                    : Mono.empty();
            return toManager.then(toAdmin);
        }), "EXPENSE");
    }

    /** La dépense passe au niveau administrateur (seuil, budget, saisie du gestionnaire). */
    public Mono<Void> expenseEscalated(Expense e, UUID actorId, String reason) {
        return safe(Mono.zip(local(actorId), lookup.vehicle(e.getVehicleId()))
                .flatMap(t -> adminApproval(e, t.getT1().orElse(null), t.getT2(), reason)), "EXPENSE");
    }

    private Mono<Void> adminApproval(Expense e, UUID actor, OperationLookup.VehicleRef v, String reason) {
        return notifier.toUser(v.companyId(), actor, "Dépense à valider par l'administrateur",
                "Dépense de " + amount(e.getAmount()) + " pour " + plate(v.plate()) + describe(e)
                        + (reason != null && !reason.isBlank() ? " (" + reason + ")" : "") + ".",
                AppNotifier.Level.WARNING, "EXPENSE_ADMIN_APPROVAL", expenseRefs(e));
    }

    /** Décision sur une dépense → chauffeur concerné, auteur et gestionnaire (si l'admin a tranché). */
    public Mono<Void> expenseDecided(Expense e, UUID actorId) {
        boolean approved = e.getStatus() == Expense.ExpenseStatus.APPROVED;
        if (!approved && e.getStatus() != Expense.ExpenseStatus.REJECTED) return Mono.empty();
        return safe(Mono.zip(local(actorId), lookup.vehicle(e.getVehicleId())).flatMap(t -> {
            List<UUID> to = new ArrayList<>();
            if (e.getDriverId() != null) to.add(e.getDriverId());
            if (e.getCreatedBy() != null) to.add(e.getCreatedBy());
            if (e.getManagerId() != null) to.add(e.getManagerId());
            return notifier.toUsers(to, t.getT1().orElse(null),
                    approved ? "Dépense approuvée" : "Dépense rejetée",
                    expenseDecisionMessage(e, t.getT2().plate()),
                    approved ? AppNotifier.Level.SUCCESS : AppNotifier.Level.WARNING,
                    approved ? "EXPENSE_APPROVED" : "EXPENSE_REJECTED", expenseRefs(e));
        }), "EXPENSE");
    }

    static String expenseDecisionMessage(Expense e, String plate) {
        String what = switch (e.getExpenseType()) {
            case FUEL -> "Votre plein";
            case MAINTENANCE -> "La dépense de maintenance";
            default -> "La dépense";
        };
        String base = what + " de " + amount(e.getAmount()) + " pour " + plate(plate);
        return e.getStatus() == Expense.ExpenseStatus.APPROVED
                ? base + " a été approuvé" + (e.getExpenseType() == Expense.ExpenseType.FUEL ? "" : "e") + "."
                : base + " a été rejeté" + (e.getExpenseType() == Expense.ExpenseType.FUEL ? "" : "e")
                  + (e.getRejectionReason() != null ? " : " + e.getRejectionReason() : "") + ".";
    }

    static Map<String, Object> expenseRefs(Expense e) {
        String sourceKey = switch (e.getExpenseType()) {
            case FUEL -> "fuelRechargeId";
            case MAINTENANCE -> "maintenanceId";
            case INCIDENT, FINE -> "incidentId";
            default -> "sourceId";
        };
        return AppNotifier.refs("expenseId", e.getId(), sourceKey, e.getSourceId(),
                "vehicleId", e.getVehicleId(), "driverId", e.getDriverId());
    }

    private static String describe(Expense e) {
        return e.getDescription() != null && !e.getDescription().isBlank() ? " : " + e.getDescription().trim() : "";
    }

    // ── Outils ───────────────────────────────────────────────────────────────

    private Mono<java.util.Optional<UUID>> local(UUID actorId) {
        return lookup.localUser(actorId).map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty());
    }

    private Mono<Void> safe(Mono<Void> work, String domain) {
        return work.onErrorResume(e -> {
            log.warn("⚠️ [NOTIF {}] non envoyée : {}", domain, e.getMessage());
            return Mono.empty();
        });
    }

    static String plate(String plate) {
        return plate == null || plate.isBlank() ? "le véhicule" : plate;
    }

    static String liters(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    static String amount(BigDecimal value) {
        if (value == null) return "0 XAF";
        java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols(java.util.Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        return new java.text.DecimalFormat("#,##0.##", symbols).format(value) + " XAF";
    }

    static String typeLabel(String type) {
        return switch (type) {
            case "PREVENTIVE" -> "préventive";
            case "CORRECTIVE" -> "corrective";
            case "EMERGENCY" -> "urgence";
            default -> type.toLowerCase();
        };
    }

    static String subjectLabel(Maintenance m) {
        String s = m.getSubject();
        if (s != null && Maintenance.TYPES.contains(s)) return "Maintenance " + typeLabel(s);
        return s;
    }
}
