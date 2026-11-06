package com.yowyob.fleet.application.service;

import com.yowyob.fleet.application.service.AppNotifier.Level;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripMissionSubmissionEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripStopEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripStopR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notifications du cycle de vie d'un trajet entre le gestionnaire et le chauffeur.
 *
 * Gestionnaire → chauffeur : trajet assigné / retiré / modifié / annulé / supprimé, départ et retour
 * enregistrés, arrêt ajouté, modifié ou passé, complément de chargement validé ou refusé, trajet manqué.
 * Chauffeur → gestionnaire : arrivée en retard, écart de quantités, arrêt non servi, tournée terminée,
 * changement de chargement signalé. Une arrivée ou une livraison sans anomalie ne notifie pas (le suivi
 * en temps réel l'affiche), pour ne pas noyer le gestionnaire.
 *
 * Données jointes : {@code tripId} (sauf trajet supprimé), {@code stopId} et {@code submissionId} quand
 * l'événement porte sur un arrêt ou un complément. Les envois ne font jamais échouer l'action métier.
 */
@Service
@RequiredArgsConstructor
public class TripNotifier {

    public static final String TRIP_ASSIGNED = "TRIP_ASSIGNED";
    public static final String TRIP_UNASSIGNED = "TRIP_UNASSIGNED";
    public static final String TRIP_UPDATED = "TRIP_UPDATED";
    public static final String TRIP_CANCELLED = "TRIP_CANCELLED";
    public static final String TRIP_DELETED = "TRIP_DELETED";
    public static final String TRIP_DEPARTED = "TRIP_DEPARTED";
    public static final String TRIP_COMPLETED = "TRIP_COMPLETED";
    public static final String TRIP_MISSED = "TRIP_MISSED";
    public static final String TRIP_TOUR_DONE = "TRIP_TOUR_DONE";
    public static final String TRIP_STOP_FAILED = "TRIP_STOP_FAILED";
    public static final String STOP_ADDED = "STOP_ADDED";
    public static final String STOP_UPDATED = "STOP_UPDATED";
    public static final String STOP_SKIPPED = "STOP_SKIPPED";
    public static final String STOP_LATE_ARRIVAL = "STOP_LATE_ARRIVAL";
    public static final String STOP_SHORTAGE = "STOP_SHORTAGE";
    public static final String SUBMISSION_CREATED = "SUBMISSION_CREATED";
    public static final String SUBMISSION_APPROVED = "SUBMISSION_APPROVED";
    public static final String SUBMISSION_REJECTED = "SUBMISSION_REJECTED";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private static final Map<String, String> FAILURE_LABELS = Map.of(
            "REFUSED", "refus du client",
            "ABSENT", "client absent",
            "CLOSED", "établissement fermé",
            "ADDRESS_NOT_FOUND", "adresse introuvable",
            "DAMAGED", "marchandise endommagée",
            "NO_TIME", "manque de temps",
            "OTHER", "autre motif");

    private final AppNotifier notifier;
    private final VehicleLocalR2dbcRepository vehicles;
    private final TripStopR2dbcRepository stops;

    // ── Gestionnaire → chauffeur ──────────────────────────────────────────────

    /** Nouveau trajet (planification, ou chauffeur remplaçant) : tout ce qu'il faut pour s'organiser. */
    public Mono<Void> assigned(TripEntity trip, UUID actorId) {
        return Mono.zip(plate(trip.getVehicleId()), stopCount(trip.getId()))
                .flatMap(t -> notifier.toUser(trip.getDriverId(), actorId, "Nouveau trajet assigné",
                        "Trajet " + code(trip) + " le " + when(trip.getStartDate(), trip.getStartTime())
                                + " : " + t.getT2() + " arrêt(s)" + vehiclePart(t.getT1())
                                + returnPart(trip.getScheduledReturnDatetime()) + missionPart(trip.getMissionObject()) + ".",
                        Level.INFO, TRIP_ASSIGNED, refs(trip)));
    }

    /** L'ancien chauffeur d'un trajet réattribué. */
    public Mono<Void> unassigned(TripEntity trip, UUID formerDriverId, UUID actorId) {
        return notifier.toUser(formerDriverId, actorId, "Trajet retiré",
                "Le trajet " + code(trip) + " du " + when(trip.getStartDate(), trip.getStartTime())
                        + " a été confié à un autre chauffeur : vous n'êtes plus attendu sur cette tournée.",
                Level.WARNING, TRIP_UNASSIGNED, refs(trip));
    }

    /** Modifications du trajet (horaire, véhicule, mission, arrêts, manifeste), résumées. */
    public Mono<Void> updated(TripEntity trip, UUID actorId, List<String> changes) {
        if (changes == null || changes.isEmpty()) return Mono.empty();
        return notifier.toUser(trip.getDriverId(), actorId, "Trajet modifié",
                "Trajet " + code(trip) + " : " + String.join(", ", changes) + ".",
                Level.INFO, TRIP_UPDATED, refs(trip));
    }

    /** Libellé « véhicule (plaque) » pour {@link #updated}. */
    public Mono<String> vehicleChange(UUID vehicleId) {
        return plate(vehicleId).map(p -> p.isBlank() ? "véhicule changé" : "véhicule changé (" + p + ")");
    }

    public Mono<Void> cancelled(TripEntity trip, UUID actorId, String reason) {
        String why = clean(reason) != null ? " Motif : " + clean(reason) + "." : "";
        String message = "Le trajet " + code(trip) + " du " + when(trip.getStartDate(), trip.getStartTime())
                + " est annulé." + why;
        return notifier.toUser(trip.getDriverId(), actorId, "Trajet annulé", message, Level.WARNING, TRIP_CANCELLED, refs(trip))
                .then(notifier.toFleetManager(trip.getFleetId(), actorId, "Trajet annulé", message,
                        Level.WARNING, TRIP_CANCELLED, refs(trip)));
    }

    /** Trajet planifié supprimé : plus de fiche à ouvrir, d'où l'absence de tripId. */
    public Mono<Void> deleted(TripEntity trip, UUID actorId) {
        if (!"SCHEDULED".equals(trip.getStatus())) return Mono.empty();
        return notifier.toUser(trip.getDriverId(), actorId, "Trajet supprimé",
                "Le trajet " + code(trip) + " prévu le " + when(trip.getStartDate(), trip.getStartTime())
                        + " a été supprimé du planning.",
                Level.WARNING, TRIP_DELETED, Map.of());
    }

    /** Départ enregistré par le gestionnaire : la tournée devient le trajet actif du chauffeur. */
    public Mono<Void> departed(TripEntity trip, UUID actorId) {
        return stopCount(trip.getId()).flatMap(count -> {
            String message = "Départ du trajet " + code(trip) + " enregistré à "
                    + (trip.getStartTime() != null ? trip.getStartTime().format(HOUR) : "-")
                    + " : votre tournée de " + count + " arrêt(s) est en cours.";
            return notifier.toUser(trip.getDriverId(), actorId, "Départ enregistré", message,
                            Level.SUCCESS, TRIP_DEPARTED, refs(trip))
                    .then(notifier.toFleetManager(trip.getFleetId(), actorId, "Départ enregistré", message,
                            Level.INFO, TRIP_DEPARTED, refs(trip)));
        });
    }

    /** Retour à la base enregistré : trajet terminé. */
    public Mono<Void> completed(TripEntity trip, UUID actorId) {
        String km = trip.getComputedDistanceKm() != null
                ? " Distance : " + trip.getComputedDistanceKm().stripTrailingZeros().toPlainString() + " km." : "";
        String message = "Retour du trajet " + code(trip) + " enregistré : trajet terminé." + km;
        return notifier.toUser(trip.getDriverId(), actorId, "Trajet terminé", message, Level.SUCCESS, TRIP_COMPLETED, refs(trip))
                .then(notifier.toFleetManager(trip.getFleetId(), actorId, "Trajet terminé", message,
                        Level.SUCCESS, TRIP_COMPLETED, refs(trip)));
    }

    /** Trajet non parti à temps (automatisme) : le gestionnaire ET le chauffeur sont prévenus. */
    public Mono<Void> missed(UUID tripId, String tripCode, UUID fleetId, UUID driverId, int hours) {
        Map<String, Object> refs = AppNotifier.refs("tripId", tripId);
        return notifier.toFleetManager(fleetId, null, "Trajet manqué",
                        "Le trajet " + tripCode + " n'est pas parti dans les " + hours
                                + " h suivant l'heure prévue : il est marqué manqué.",
                        Level.WARNING, TRIP_MISSED, refs)
                .then(notifier.toUser(driverId, null, "Trajet manqué",
                        "Le trajet " + tripCode + " n'a pas démarré dans les " + hours
                                + " h suivant l'heure prévue : il est marqué manqué. Rapprochez-vous de votre gestionnaire.",
                        Level.WARNING, TRIP_MISSED, refs));
    }

    public Mono<Void> stopAdded(TripEntity trip, TripStopEntity stop, UUID actorId) {
        boolean enRoute = !"SCHEDULED".equals(trip.getStatus());
        return notifier.toUser(trip.getDriverId(), actorId, enRoute ? "Nouvel arrêt sur votre tournée" : "Arrêt ajouté",
                "Trajet " + code(trip) + " : arrêt " + stop.getSequence() + " « " + stop.getLocation() + " » ajouté ("
                        + stopKind(stop) + plannedPart(stop.getPlannedArrival()) + ").",
                enRoute ? Level.WARNING : Level.INFO, STOP_ADDED, refs(trip, stop));
    }

    public Mono<Void> stopUpdated(TripEntity trip, TripStopEntity stop, UUID actorId) {
        return notifier.toUser(trip.getDriverId(), actorId, "Arrêt modifié",
                "Trajet " + code(trip) + " : l'arrêt " + stop.getSequence() + " « " + stop.getLocation()
                        + " » a été mis à jour (lieu, contact, consignes ou heure prévue).",
                Level.INFO, STOP_UPDATED, refs(trip, stop));
    }

    public Mono<Void> stopSkipped(TripEntity trip, TripStopEntity stop, String reason, UUID actorId) {
        return notifier.toUser(trip.getDriverId(), actorId, "Arrêt retiré de la tournée",
                "Trajet " + code(trip) + " : ne vous rendez pas à l'arrêt « " + stop.getLocation() + " »."
                        + (clean(reason) != null ? " Motif : " + clean(reason) + "." : ""),
                Level.WARNING, STOP_SKIPPED, refs(trip, stop));
    }

    public Mono<Void> submissionReviewed(TripEntity trip, TripMissionSubmissionEntity sub, boolean approved, UUID actorId) {
        String what = ("REMOVE".equals(sub.getChangeType()) ? "retrait" : "ajout") + " de "
                + (clean(sub.getDescription()) != null ? "« " + clean(sub.getDescription()) + " »" : "chargement")
                + (sub.getQuantity() != null ? " × " + sub.getQuantity() : "");
        Map<String, Object> refs = AppNotifier.refs("tripId", trip.getId(), "submissionId", sub.getId());
        if (approved) {
            return notifier.toUser(sub.getSubmittedBy(), actorId, "Changement de chargement validé",
                    "Trajet " + code(trip) + " : " + what + " validé, le manifeste est à jour.",
                    Level.SUCCESS, SUBMISSION_APPROVED, refs);
        }
        return notifier.toUser(sub.getSubmittedBy(), actorId, "Changement de chargement refusé",
                "Trajet " + code(trip) + " : " + what + " refusé."
                        + (clean(sub.getRejectionReason()) != null ? " Motif : " + clean(sub.getRejectionReason()) + "." : ""),
                Level.WARNING, SUBMISSION_REJECTED, refs);
    }

    // ── Chauffeur → gestionnaire ──────────────────────────────────────────────

    public Mono<Void> lateArrival(TripEntity trip, TripStopEntity stop, long delayMinutes, UUID actorId) {
        return notifier.toFleetManager(trip.getFleetId(), actorId, "Arrivée en retard",
                "Trajet " + code(trip) + " : arrivée à l'arrêt " + stop.getSequence() + " « " + stop.getLocation()
                        + " » avec " + delayMinutes + " min de retard.",
                Level.WARNING, STOP_LATE_ARRIVAL, refs(trip, stop));
    }

    public Mono<Void> stopShortage(TripEntity trip, TripStopEntity stop, int lines, UUID actorId) {
        return notifier.toFleetManager(trip.getFleetId(), actorId, "Écart de quantités",
                "Trajet " + code(trip) + " : " + lines + " ligne(s) " + ("PICKUP".equals(stop.getStopType()) ? "ramassée(s)" : "livrée(s)")
                        + " en quantité inférieure au prévu à l'arrêt « " + stop.getLocation() + " ».",
                Level.WARNING, STOP_SHORTAGE, refs(trip, stop));
    }

    public Mono<Void> stopFailed(TripEntity trip, TripStopEntity stop, UUID actorId) {
        String reason = FAILURE_LABELS.getOrDefault(stop.getFailureReason(), "motif non précisé");
        return notifier.toFleetManager(trip.getFleetId(), actorId, "Arrêt non servi",
                "Trajet " + code(trip) + " : l'arrêt « " + stop.getLocation() + " » n'a pas pu être servi (" + reason + ")."
                        + (clean(stop.getFailureNotes()) != null ? " " + clean(stop.getFailureNotes()) : ""),
                Level.WARNING, TRIP_STOP_FAILED, refs(trip, stop));
    }

    public Mono<Void> tourDone(TripEntity trip, long served, int total, UUID actorId) {
        return notifier.toFleetManager(trip.getFleetId(), actorId, "Tournée terminée",
                "Trajet " + code(trip) + " : " + served + "/" + total + " arrêt(s) servi(s), retour à la base en cours.",
                served < total ? Level.WARNING : Level.INFO, TRIP_TOUR_DONE, refs(trip));
    }

    public Mono<Void> submissionCreated(TripEntity trip, TripMissionSubmissionEntity sub, UUID actorId) {
        String what = ("REMOVE".equals(sub.getChangeType()) ? "Retrait" : "Ajout") + " de "
                + (clean(sub.getDescription()) != null ? "« " + clean(sub.getDescription()) + " »" : "chargement")
                + (sub.getQuantity() != null ? " × " + sub.getQuantity() : "");
        return notifier.toFleetManager(trip.getFleetId(), actorId, "Changement de chargement à valider",
                "Trajet " + code(trip) + " : " + what + " signalé par le chauffeur."
                        + (clean(sub.getNotes()) != null ? " " + clean(sub.getNotes()) : ""),
                Level.INFO, SUBMISSION_CREATED, AppNotifier.refs("tripId", trip.getId(), "submissionId", sub.getId()));
    }

    // ── Outils ────────────────────────────────────────────────────────────────

    static String when(LocalDate date, LocalTime time) {
        if (date == null) return "-";
        return date.format(DAY) + (time != null ? " à " + time.format(HOUR) : "");
    }

    static String when(LocalDateTime at) {
        return at == null ? "-" : when(at.toLocalDate(), at.toLocalTime());
    }

    private Mono<String> plate(UUID vehicleId) {
        if (vehicleId == null) return Mono.just("");
        return vehicles.findById(vehicleId)
                .map(v -> v.getLicensePlate() != null ? v.getLicensePlate() : "")
                .defaultIfEmpty("")
                .onErrorReturn("");
    }

    private Mono<Long> stopCount(UUID tripId) {
        return stops.findAllByTripIdOrderBySequence(tripId).count().onErrorReturn(0L);
    }

    private static Map<String, Object> refs(TripEntity trip) {
        return AppNotifier.refs("tripId", trip.getId());
    }

    private static Map<String, Object> refs(TripEntity trip, TripStopEntity stop) {
        return AppNotifier.refs("tripId", trip.getId(), "stopId", stop.getId());
    }

    private static String code(TripEntity trip) {
        return trip.getTripCode() != null ? trip.getTripCode() : "";
    }

    private static String vehiclePart(String plate) {
        return plate == null || plate.isBlank() ? "" : ", véhicule " + plate;
    }

    private static String returnPart(LocalDateTime scheduledReturn) {
        return scheduledReturn == null ? "" : ", retour prévu le " + when(scheduledReturn);
    }

    private static String missionPart(String mission) {
        return clean(mission) == null ? "" : ". Mission : " + clean(mission);
    }

    private static String plannedPart(LocalDateTime planned) {
        return planned == null ? "" : ", prévu le " + when(planned);
    }

    private static String stopKind(TripStopEntity stop) {
        return "PICKUP".equals(stop.getStopType()) ? "ramassage" : "livraison";
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
