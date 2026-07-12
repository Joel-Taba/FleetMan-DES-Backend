package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.Trip;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Port d'entrée pour la gestion des Trajets.
 * Nouveau flux : le Fleet Manager crée et pilote les trajets (plus le
 * chauffeur).
 */
public interface ManageTripUseCase {
    // ── Commandes imbriquées ─────────────────────────────────────────────────

    record TripDetailInput(
            String itemType,
            String description,
            int quantity,
            BigDecimal weight,
            Integer departureQuantity) {
    }

    record CreateTripCommand(
            UUID vehicleId,
            UUID driverId,
            UUID fleetId,
            UUID managerId,
            LocalDate startDate,
            LocalTime startTime,
            String departureLocation,
            BigDecimal departureKmIndex,
            BigDecimal departureFuelIndex,
            String missionObject,
            BigDecimal missionCost,
            String rateType,
            LocalDateTime scheduledReturnDatetime,
            List<TripDetailInput> details) {
    }

    record RegisterReturnCommand(
            String tripCode,
            LocalDate returnDate,
            LocalTime returnTime,
            String returnLocation,
            BigDecimal returnKmIndex,
            BigDecimal returnFuelIndex,
            List<ReturnDetailInput> detailUpdates) {
    }

    record ReturnDetailInput(UUID detailId, Integer returnQuantity) {
    }

    // ── Opérations Manager ───────────────────────────────────────────────────

    /** Crée un trajet (départ). Réservé au Fleet Manager. */
    Mono<Trip> createTrip(CreateTripCommand command);

    /** Enregistre le retour d'un trajet à partir de son code. */
    Mono<Trip> registerReturn(RegisterReturnCommand command);

    /** Retrouve un trajet par son code (ex: TRJ-2026-0001). */
    Mono<Trip> getTripByCode(String tripCode);

    /** Change le conducteur d'un trajet en statut SCHEDULED. */
    Mono<Trip> updateTripDriver(UUID tripId, UUID newDriverId, UUID managerId);

    /** Annule un trajet avec un motif. */
    Mono<Trip> cancelTrip(UUID tripId, String reason, UUID managerId);

    /** Liste tous les trajets du manager (filtre optionnel par flotte). */
    Flux<Trip> getManagerTrips(UUID managerId, UUID optionalFleetId);

    /** Récupère le détail d'un trajet. */
    Mono<Trip> getTripById(UUID tripId);

    Mono<Trip> startTrip(UUID tripId, BigDecimal departureKmIndex, BigDecimal departureFuelIndex,
            String departureLocation);

    Mono<Trip> returningTrip(UUID tripId);

    Mono<Trip> completeTrip(UUID tripId, BigDecimal returnKmIndex, BigDecimal returnFuelIndex, String returnLocation);

    // ── Télémétrie (conservée pour usage futur app mobile chauffeur) ─────────

    /** Enregistre un point GPS et vérifie les zones de géofencing. */
    Mono<Void> sendTelemetry(UUID tripId, Double lat, Double lng, Double speed);

    /** Récupère le trajet actif du chauffeur (lecture seule). */
    Mono<Trip> getMyActiveTrip(UUID driverId);

    /** Historique personnel du chauffeur. */
    Flux<Trip> getMyTripHistory(UUID driverId);
}
