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
 * Nouveau flux : le Fleet Manager crée et pilote les trajets (plus le chauffeur).
 */
public interface ManageTripUseCase {
    // ── Commandes imbriquées ─────────────────────────────────────────────────

    record TripDetailInput(
        String itemType,
        String description,
        int quantity,
        BigDecimal weight,
        Integer departureQuantity
    ) {}

    record CreateTripCommand(
        UUID vehicleId,
        UUID driverId,
        UUID fleetId,
        UUID managerId,
        LocalDate startDate,
        LocalTime startTime,
        String departureLocation,
        BigDecimal departureLat,
        BigDecimal departureLng,
        BigDecimal departureKmIndex,
        BigDecimal departureFuelIndex,
        String missionObject,
        BigDecimal missionCost,
        String missionCostCurrency,
        String rateType,
        LocalDateTime scheduledReturnDatetime,
        List<TripDetailInput> details
    ) {}

    record UpdateTripCommand(
        UUID managerId,
        UUID vehicleId,
        UUID driverId,
        LocalDate startDate,
        LocalTime startTime,
        String departureLocation,
        BigDecimal departureLat,
        BigDecimal departureLng,
        BigDecimal departureKmIndex,
        BigDecimal departureFuelIndex,
        String missionObject,
        BigDecimal missionCost,
        String missionCostCurrency
    ) {}

    record MissionSubmissionInput(
        String itemType,
        String description,
        Integer quantity,
        BigDecimal weight,
        String notes
    ) {}

    record RegisterReturnCommand(
        String tripCode,
        LocalDate returnDate,
        LocalTime returnTime,
        String returnLocation,
        BigDecimal returnLat,
        BigDecimal returnLng,
        BigDecimal returnKmIndex,
        BigDecimal returnFuelIndex,
        List<ReturnDetailInput> detailUpdates
    ) {}

    record ReturnDetailInput(UUID detailId, Integer returnQuantity) {}

    // ── Opérations Manager ───────────────────────────────────────────────────

    /** Crée un trajet (départ). Réservé au Fleet Manager. */
    Mono<Trip> createTrip(CreateTripCommand command);

    /** Lance effectivement un trajet planifié (SCHEDULED → DEPARTED). */
    Mono<Trip> startTrip(UUID tripId, UUID managerId);

    /** Enregistre le retour d'un trajet à partir de son code. */
    Mono<Trip> registerReturn(RegisterReturnCommand command);

    /** Retrouve un trajet par son code (ex: TRJ-2026-0001). */
    Mono<Trip> getTripByCode(String tripCode);

    /** Change le conducteur d'un trajet non clôturé. */
    Mono<Trip> updateTripDriver(UUID tripId, UUID newDriverId, UUID managerId);

    /** Change le véhicule d'un trajet non clôturé. */
    Mono<Trip> updateTripVehicle(UUID tripId, UUID newVehicleId, UUID managerId);

    /** Met à jour les informations de départ / mission tant que le trajet n'est pas clôturé. */
    Mono<Trip> updateTrip(UUID tripId, UpdateTripCommand command);

    /** Trajets en cours (départ enregistré, retour non clôturé). */
    Flux<Trip> getOpenTrips(UUID managerId);

    /** Complément d'information soumis par le conducteur (en attente validation). */
    Mono<UUID> submitMissionComplement(
        UUID tripId,
        UUID driverId,
        MissionSubmissionInput input
    );

    /** Valide un complément conducteur et l'intègre aux détails du trajet. */
    Mono<Trip> approveMissionSubmission(UUID submissionId, UUID managerId);

    /** Rejette un complément conducteur. */
    Mono<Void> rejectMissionSubmission(UUID submissionId, UUID managerId);

    /** Annule un trajet avec un motif. */
    Mono<Trip> cancelTrip(UUID tripId, String reason, UUID managerId);

    /** Liste tous les trajets du manager (filtre optionnel par flotte). */
    Flux<Trip> getManagerTrips(UUID managerId, UUID optionalFleetId);

    /** Liste avec filtres status / vehicleId (côté Manager UI). */
    default Flux<Trip> getManagerTrips(UUID managerId, UUID optionalFleetId, String status, UUID vehicleId) {
        Flux<Trip> trips = getManagerTrips(managerId, optionalFleetId);
        if (status != null && !status.isBlank()) {
            trips = trips.filter(t -> t.getStatus() != null && status.equalsIgnoreCase(t.getStatus().name()));
        }
        if (vehicleId != null) {
            trips = trips.filter(t -> vehicleId.equals(t.getVehicleId()));
        }
        return trips;
    }

    /** Suppression d'un trajet (SCHEDULED / CANCELLED uniquement). */
    Mono<Void> deleteTrip(UUID tripId, UUID managerId);

    /** Récupère le détail d'un trajet. */
    Mono<Trip> getTripById(UUID tripId);

    // ── Télémétrie (conservée pour usage futur app mobile chauffeur) ─────────

    /** Enregistre un point GPS et vérifie les zones de géofencing. */
    Mono<Void> sendTelemetry(UUID tripId, Double lat, Double lng, Double speed);

    /** Récupère le trajet actif du chauffeur (lecture seule). */
    Mono<Trip> getMyActiveTrip(UUID driverId);

    /** Historique personnel du chauffeur. */
    Flux<Trip> getMyTripHistory(UUID driverId);
}
