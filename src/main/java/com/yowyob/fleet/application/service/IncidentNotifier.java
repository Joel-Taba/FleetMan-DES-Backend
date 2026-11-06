package com.yowyob.fleet.application.service;

import com.yowyob.fleet.application.service.AppNotifier.Level;
import com.yowyob.fleet.domain.model.Incident;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Notifications in-app du dossier d'incident, entre le chauffeur (côté terrain) et le gestionnaire
 * de la flotte (côté bureau), via {@link AppNotifier}. Toutes portent {@code incidentId} ; l'auteur
 * de l'action n'est jamais notifié.
 *
 * <ul>
 *   <li>côté bureau : le gestionnaire de la flotte du véhicule, le responsable du dossier, et le
 *       déclarant s'il n'est pas chauffeur ;</li>
 *   <li>côté terrain : le chauffeur concerné, et le déclarant s'il est chauffeur.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class IncidentNotifier {

    public static final String DECLARED = "INCIDENT_DECLARED";
    public static final String STATUS_CHANGED = "INCIDENT_STATUS_CHANGED";
    public static final String ASSIGNED = "INCIDENT_ASSIGNED";
    public static final String COMMENT = "INCIDENT_COMMENT";
    public static final String INFO_REQUESTED = "INCIDENT_INFO_REQUESTED";
    public static final String DRIVER_REPLY = "INCIDENT_DRIVER_REPLY";
    public static final String ATTACHMENT_ADDED = "INCIDENT_ATTACHMENT_ADDED";
    public static final String VEHICLE_IMMOBILIZED = "INCIDENT_VEHICLE_IMMOBILIZED";
    public static final String VEHICLE_RELEASED = "INCIDENT_VEHICLE_RELEASED";
    public static final String REPAIR_ORDER = "INCIDENT_REPAIR_ORDER";
    public static final String TRIP_INTERRUPTED = "INCIDENT_TRIP_INTERRUPTED";
    public static final String TRIP_RESUMED = "INCIDENT_TRIP_RESUMED";

    private final AppNotifier notifier;
    private final DatabaseClient db;

    /** Les personnes concernées par un dossier. */
    public record Parties(UUID fleetManagerId, UUID handlerId, UUID driverId, UUID reporterId, boolean reporterIsDriver,
                          UUID tripId) {

        /** Côté bureau : gestionnaire de la flotte, responsable du dossier, déclarant non chauffeur. */
        public Set<UUID> staff() {
            Set<UUID> out = new LinkedHashSet<>();
            if (fleetManagerId != null) out.add(fleetManagerId);
            if (handlerId != null) out.add(handlerId);
            if (reporterId != null && !reporterIsDriver) out.add(reporterId);
            return out;
        }

        /** Côté terrain : chauffeur concerné, déclarant chauffeur. */
        public Set<UUID> field() {
            Set<UUID> out = new LinkedHashSet<>();
            if (driverId != null) out.add(driverId);
            if (reporterId != null && reporterIsDriver) out.add(reporterId);
            return out;
        }

        public Set<UUID> everyone() {
            Set<UUID> out = new LinkedHashSet<>(field());
            out.addAll(staff());
            return out;
        }
    }

    public Mono<Parties> parties(UUID incidentId) {
        return db.sql("""
                        SELECT COALESCE(f.manager_id, v.manager_id) AS fleet_manager, c.handler_id, i.driver_id, c.reported_by_id, c.trip_id,
                               EXISTS (SELECT 1 FROM fleet.drivers d WHERE d.user_id = c.reported_by_id) AS reporter_is_driver
                        FROM fleet.incidents i
                        LEFT JOIN fleet.vehicles v ON v.id = i.vehicle_id
                        LEFT JOIN fleet.fleets f ON f.id = v.fleet_id
                        LEFT JOIN fleet.incident_cases c ON c.incident_id = i.id
                        WHERE i.id = :id
                        """)
                .bind("id", incidentId)
                .map((r, m) -> new Parties(r.get("fleet_manager", UUID.class), r.get("handler_id", UUID.class),
                        r.get("driver_id", UUID.class), r.get("reported_by_id", UUID.class),
                        Boolean.TRUE.equals(r.get("reporter_is_driver", Boolean.class)), r.get("trip_id", UUID.class)))
                .one()
                .onErrorResume(e -> Mono.empty());
    }

    // ── Textes ───────────────────────────────────────────────────────────────

    static String subject(Incident i) {
        String type = i.getType() != null ? i.getType().label() : "Incident";
        String plate = i.getVehicleRegistration() != null ? i.getVehicleRegistration() : "le véhicule";
        return type + " sur " + plate;
    }

    static String statusTitle(Incident.Status from, Incident.Status to) {
        if (from == Incident.Status.RESOLVED && to == Incident.Status.UNDER_INVESTIGATION) return "Incident rouvert";
        return switch (to) {
            case ACKNOWLEDGED -> "Incident pris en charge";
            case UNDER_INVESTIGATION -> "Incident en traitement";
            case RESOLVED -> "Incident résolu";
            case CLOSED -> "Incident clôturé";
            case CANCELLED -> "Incident annulé";
            default -> "Suivi d'incident";
        };
    }

    static String statusText(Incident.Status from, Incident.Status to) {
        if (from == Incident.Status.RESOLVED && to == Incident.Status.UNDER_INVESTIGATION) return "a été rouvert";
        return switch (to) {
            case ACKNOWLEDGED -> "a été pris en charge";
            case UNDER_INVESTIGATION -> "est en cours de traitement";
            case RESOLVED -> "est résolu";
            case CLOSED -> "est clôturé";
            case CANCELLED -> "a été annulé";
            default -> "a changé de statut";
        };
    }

    private static String quote(String note) {
        if (note == null || note.isBlank()) return "";
        String text = note.trim();
        return text.length() > 160 ? text.substring(0, 157) + "..." : text;
    }

    private static Map<String, Object> refs(Incident i, Object... extra) {
        Object[] all = new Object[4 + extra.length];
        all[0] = "incidentId";
        all[1] = i.getId();
        all[2] = "vehicleId";
        all[3] = i.getVehicleId();
        System.arraycopy(extra, 0, all, 4, extra.length);
        return AppNotifier.refs(all);
    }

    // ── Événements ───────────────────────────────────────────────────────────

    /**
     * Déclaration : le bureau est prévenu tout de suite (alerte si grave ou véhicule non roulant) ;
     * le chauffeur concerné l'est quand c'est le bureau qui déclare.
     */
    public Mono<Void> declared(Incident i, UUID actor, String impact) {
        boolean serious = i.isCritical() || !i.isVehicleDrivable();
        String severity = i.getSeverity() != null ? " (gravité " + i.getSeverity().label() + ")" : "";
        String by = i.getDriverFullName() != null ? ", signalé par " + i.getDriverFullName() : "";
        String drivable = i.isVehicleDrivable() ? "" : " Le véhicule ne peut plus rouler.";
        String staffMessage = subject(i) + severity + by + "." + drivable + (impact != null ? impact : "");
        String fieldMessage = "Votre gestionnaire a déclaré un incident vous concernant : " + subject(i) + severity + "." + drivable;
        return parties(i.getId()).flatMap(p -> notifier.toUsers(p.staff(), actor,
                        i.isCritical() ? "Incident grave signalé" : "Incident signalé",
                        staffMessage, serious ? Level.WARNING : Level.INFO, DECLARED, refs(i, "tripId", p.tripId()))
                .then(notifier.toUsers(p.field(), actor, "Incident déclaré vous concernant", fieldMessage,
                        serious ? Level.WARNING : Level.INFO, DECLARED, refs(i, "tripId", p.tripId()))));
    }

    /** Chaque étape du dossier : tout le monde sauf l'auteur, avec le motif s'il y en a un. */
    public Mono<Void> statusChanged(Incident i, Incident.Status from, Incident.Status to, UUID actor, String note) {
        String message = "L'incident " + subject(i) + " " + statusText(from, to) + "."
                + (quote(note).isEmpty() ? "" : (to == Incident.Status.RESOLVED ? " Résolution : " : " Motif : ") + quote(note));
        Level level = to == Incident.Status.RESOLVED || to == Incident.Status.CLOSED ? Level.SUCCESS
                : to == Incident.Status.CANCELLED ? Level.WARNING : Level.INFO;
        return parties(i.getId()).flatMap(p -> notifier.toUsers(p.everyone(), actor, statusTitle(from, to), message, level,
                STATUS_CHANGED, refs(i, "status", to.name())));
    }

    /** Le dossier est confié à quelqu'un d'autre que l'auteur de la prise en charge. */
    public Mono<Void> assigned(Incident i, UUID handlerId, UUID actor) {
        return notifier.toUser(handlerId, actor, "Dossier d'incident confié",
                "Le dossier " + subject(i) + " vous a été confié.", Level.INFO, ASSIGNED, refs(i));
    }

    /** Message du bureau au chauffeur (simple message ou demande d'information). */
    public Mono<Void> staffMessage(Incident i, UUID actor, boolean requestInfo, String text) {
        return parties(i.getId()).flatMap(p -> requestInfo
                ? notifier.toUsers(p.field(), actor, "Information demandée",
                        "Votre gestionnaire demande des précisions sur l'incident " + subject(i) + " : " + quote(text),
                        Level.WARNING, INFO_REQUESTED, refs(i))
                : notifier.toUsers(p.field(), actor, "Nouveau message sur votre incident",
                        subject(i) + " : " + quote(text), Level.INFO, COMMENT, refs(i)));
    }

    /** Message ou réponse du chauffeur au bureau. */
    public Mono<Void> driverMessage(Incident i, UUID actor, boolean answersRequest, String text) {
        return parties(i.getId()).flatMap(p -> notifier.toUsers(p.staff(), actor,
                answersRequest ? "Réponse du chauffeur" : "Message du chauffeur",
                subject(i) + " : " + quote(text), Level.INFO, DRIVER_REPLY, refs(i)));
    }

    /**
     * Pièce ajoutée : l'autre côté est prévenu. Plusieurs photos envoyées d'affilée (ou juste après la
     * déclaration) ne produisent qu'une notification tant que la précédente n'est pas lue.
     */
    public Mono<Void> attachmentAdded(Incident i, UUID actor, boolean fromDriver, String fileName) {
        String what = fileName != null && !fileName.isBlank() ? " (" + quote(fileName) + ")" : "";
        return parties(i.getId()).flatMap(p -> Flux.fromIterable(fromDriver ? p.staff() : p.field())
                .filterWhen(u -> recentlyNotified(u, i.getId()).map(seen -> !seen))
                .collectList()
                .flatMap(users -> fromDriver
                        ? notifier.toUsers(users, actor, "Pièce ajoutée par le chauffeur",
                                "Nouvelle pièce sur le dossier " + subject(i) + what + ".", Level.INFO, ATTACHMENT_ADDED, refs(i))
                        : notifier.toUsers(users, actor, "Pièce ajoutée à votre incident",
                                "Votre gestionnaire a ajouté une pièce au dossier " + subject(i) + what + ".", Level.INFO,
                                ATTACHMENT_ADDED, refs(i))));
    }

    /** Une notification non lue de déclaration ou de pièce sur ce dossier date de moins de 15 minutes. */
    Mono<Boolean> recentlyNotified(UUID userId, UUID incidentId) {
        return db.sql("""
                        SELECT EXISTS (SELECT 1 FROM fleet.notifications n
                                       WHERE n.user_id = :user AND n.is_read = FALSE AND n.created_at > :since
                                         AND n.data LIKE :incident
                                         AND (n.data LIKE '%"INCIDENT_DECLARED"%' OR n.data LIKE '%"INCIDENT_ATTACHMENT_ADDED"%')) AS seen
                        """)
                .bind("user", userId)
                .bind("since", LocalDateTime.now().minusMinutes(15))
                .bind("incident", "%\"incidentId\":\"" + incidentId + "\"%")
                .map((r, m) -> Boolean.TRUE.equals(r.get("seen", Boolean.class)))
                .one()
                .defaultIfEmpty(false)
                .onErrorResume(e -> Mono.just(false));
    }

    /** Décision d'immobiliser ou de remettre en service le véhicule. */
    public Mono<Void> immobilization(Incident i, boolean immobilized, UUID actor, String reason) {
        String message = immobilized
                ? "Le véhicule " + i.getVehicleRegistration() + " est immobilisé (incident " + subject(i) + "). Motif : " + quote(reason)
                : "Le véhicule " + i.getVehicleRegistration() + " est remis en service (incident " + subject(i) + "). Motif : " + quote(reason);
        return parties(i.getId()).flatMap(p -> notifier.toUsers(p.everyone(), actor,
                immobilized ? "Véhicule immobilisé" : "Véhicule remis en service", message,
                immobilized ? Level.WARNING : Level.SUCCESS, immobilized ? VEHICLE_IMMOBILIZED : VEHICLE_RELEASED, refs(i)));
    }

    /** Ordre de réparation ouvert pour le dossier. */
    public Mono<Void> repairOrder(Incident i, UUID actor, UUID maintenanceId, String title) {
        return parties(i.getId()).flatMap(p -> notifier.toUsers(p.field(), actor, "Réparation programmée",
                "Un ordre de réparation est ouvert pour l'incident " + subject(i) + " : " + quote(title) + ".",
                Level.INFO, REPAIR_ORDER, refs(i, "maintenanceId", maintenanceId)));
    }

    /** Mission interrompue par l'immobilisation du véhicule : le chauffeur du trajet attend les consignes. */
    public Mono<Void> tripInterrupted(Incident i, UUID tripId, String tripCode, UUID tripDriverId, UUID actor) {
        String trip = tripCode != null ? "Le trajet " + tripCode : "Votre trajet en cours";
        return notifier.toUser(tripDriverId, actor, "Mission interrompue",
                trip + " est interrompu : le véhicule " + i.getVehicleRegistration()
                        + " est immobilisé suite à un incident. Attendez les consignes de votre gestionnaire.",
                Level.WARNING, TRIP_INTERRUPTED, refs(i, "tripId", tripId));
    }

    /** Véhicule libéré : la mission interrompue reprend (chauffeur du trajet et gestionnaire de la flotte). */
    public Mono<Void> tripResumed(Incident i, UUID tripId, String tripCode, UUID tripDriverId, UUID tripFleetId, UUID actor) {
        String trip = tripCode != null ? "le trajet " + tripCode : "le trajet en cours";
        String message = "Le véhicule " + i.getVehicleRegistration() + " est de nouveau utilisable : " + trip + " reprend.";
        Map<String, Object> refs = refs(i, "tripId", tripId);
        return notifier.toUser(tripDriverId, actor, "Mission reprise", message, Level.SUCCESS, TRIP_RESUMED, refs)
                .then(notifier.toFleetManager(tripFleetId, actor, "Mission reprise", message, Level.SUCCESS, TRIP_RESUMED, refs));
    }
}
