package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.Trip;
import com.yowyob.fleet.domain.ports.in.ManageTripUseCase;
import com.yowyob.fleet.domain.ports.in.TrackTripUseCase;
import com.yowyob.fleet.domain.model.TripTracking;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.TelemetryRequest;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripMissionSubmissionEntity;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TripController {

    private final ManageTripUseCase tripUseCase;
    private final TrackTripUseCase trackingUseCase;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record TripDetailInputDto(
        @NotBlank String itemType,
        String description,
        @NotNull int quantity,
        BigDecimal weight,
        Integer departureQuantity,
        Integer stopIndex
    ) {}

    public record TripStopRequest(
        @NotBlank String stopType,
        @NotBlank(message = "Le lieu de chaque arrêt est obligatoire.") String location,
        BigDecimal latitude,
        BigDecimal longitude,
        String contactName,
        String contactPhone,
        String instructions,
        LocalDateTime plannedArrival
    ) {
        public com.yowyob.fleet.domain.model.TripStopRules.StopInput toInput() {
            return new com.yowyob.fleet.domain.model.TripStopRules.StopInput(stopType, location, latitude, longitude,
                contactName, contactPhone, instructions, plannedArrival);
        }
    }

    public record CreateTripRequest(
        @NotNull UUID vehicleId,
        @NotNull UUID driverId,
        UUID fleetId,
        @NotNull LocalDate startDate,
        @NotNull LocalTime startTime,
        String departureLocation,
        BigDecimal departureLat,
        BigDecimal departureLng,
        BigDecimal departureKmIndex,
        @PositiveOrZero(message = "L'index carburant (L) ne peut pas être négatif.") BigDecimal departureFuelIndex,
        String missionObject,
        /** Ancien formulaire : destination unique ; ignorée si des arrêts sont fournis. */
        String destinationLocation,
        BigDecimal destinationLat,
        BigDecimal destinationLng,
        LocalDateTime scheduledReturnDatetime,
        List<TripDetailInputDto> details,
        @jakarta.validation.Valid List<TripStopRequest> stops
    ) {}

    public record ReturnDetailInputDto(UUID detailId, Integer returnQuantity) {}

    public record RegisterReturnRequest(
        @NotBlank String tripCode,
        @NotNull LocalDate returnDate,
        @NotNull LocalTime returnTime,
        String returnLocation,
        BigDecimal returnLat,
        BigDecimal returnLng,
        BigDecimal returnKmIndex,
        @PositiveOrZero(message = "L'index carburant (L) ne peut pas être négatif.") BigDecimal returnFuelIndex,
        List<ReturnDetailInputDto> detailUpdates
    ) {}

    public record UpdateDriverRequest(@NotNull UUID newDriverId) {}

    public record UpdateVehicleRequest(@NotNull UUID newVehicleId) {}

    public record UpdateTripRequest(
        UUID vehicleId,
        UUID driverId,
        LocalDate startDate,
        LocalTime startTime,
        String departureLocation,
        BigDecimal departureLat,
        BigDecimal departureLng,
        BigDecimal departureKmIndex,
        @PositiveOrZero(message = "L'index carburant (L) ne peut pas être négatif.") BigDecimal departureFuelIndex,
        String missionObject,
        String destinationLocation,
        BigDecimal destinationLat,
        BigDecimal destinationLng,
        java.time.LocalDateTime scheduledReturnDatetime
    ) {}

    public record MissionSubmissionRequest(
        String changeType,
        UUID detailId,
        @NotBlank String itemType,
        String description,
        Integer quantity,
        BigDecimal weight,
        String notes,
        UUID stopId
    ) {}

    public record CancelTripRequest(String reason) {}

    // ── OPÉRATIONS MANAGER ───────────────────────────────────────────────────

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @PostMapping
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un trajet (départ) : réservé au Fleet Manager")
    public Mono<Trip> createTrip(
        @Valid @RequestBody CreateTripRequest req,
        Authentication auth
    ) {
        List<ManageTripUseCase.TripDetailInput> details =
            req.details() != null
                ? req
                      .details()
                      .stream()
                      .map(d ->
                          new ManageTripUseCase.TripDetailInput(
                              d.itemType(),
                              d.description(),
                              d.quantity(),
                              d.weight(),
                              d.departureQuantity(),
                              d.stopIndex()
                          )
                      )
                      .toList()
                : List.of();

        return tripUseCase.createTrip(
            new ManageTripUseCase.CreateTripCommand(
                req.vehicleId(),
                req.driverId(),
                req.fleetId(),
                getUserId(auth),
                req.startDate(),
                req.startTime(),
                req.departureLocation(),
                req.departureLat(),
                req.departureLng(),
                req.departureKmIndex(),
                req.departureFuelIndex(),
                req.missionObject(),
                req.destinationLocation(),
                req.destinationLat(),
                req.destinationLng(),
                req.scheduledReturnDatetime(),
                details,
                req.stops() != null ? req.stops().stream().map(TripStopRequest::toInput).toList() : List.of()
            )
        );
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Lancer un trajet planifié : date/heure réelles et relevés (km, carburant) au départ")
    public Mono<Trip> startTrip(
        @PathVariable UUID id,
        @RequestBody(required = false) StartTripRequest req,
        Authentication auth
    ) {
        return tripUseCase.startTrip(
            id,
            req != null ? new ManageTripUseCase.StartTripCommand(req.departureKmIndex(), req.departureFuelIndex()) : null,
            getUserId(auth)
        );
    }

    public record StartTripRequest(
        @PositiveOrZero BigDecimal departureKmIndex,
        @PositiveOrZero BigDecimal departureFuelIndex
    ) {}

    @Tag(name = OpenApiConfig.TAG_TRIPS_OPS)
    @PostMapping("/{id}/arrive")
    @PreAuthorize("hasAnyRole('FLEET_DRIVER', 'FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Arrivée à destination : lieu, heure et quantités livrées par ligne du manifeste")
    public Mono<Trip> registerArrival(
        @PathVariable UUID id,
        @Valid @RequestBody ArrivalRequest req,
        Authentication auth
    ) {
        List<ManageTripUseCase.DeliveredDetailInput> delivered = req.delivered() != null
            ? req.delivered().stream()
                .map(d -> new ManageTripUseCase.DeliveredDetailInput(d.detailId(), d.deliveredQuantity()))
                .toList()
            : List.of();
        return tripUseCase.registerArrival(
            id,
            new ManageTripUseCase.ArrivalCommand(
                req.arrivedAt(), req.arrivalLocation(), req.arrivalLat(), req.arrivalLng(), req.notes(), delivered),
            getUserId(auth)
        );
    }

    public record ArrivalRequest(
        LocalDateTime arrivedAt,
        String arrivalLocation,
        BigDecimal arrivalLat,
        BigDecimal arrivalLng,
        String notes,
        List<DeliveredDetailRequest> delivered
    ) {}

    public record DeliveredDetailRequest(@NotNull UUID detailId, @NotNull @PositiveOrZero Integer deliveredQuantity) {}

    @Tag(name = OpenApiConfig.TAG_TRIPS_OPS)
    @GetMapping("/{id}/submissions")
    @PreAuthorize("hasAnyRole('FLEET_DRIVER', 'FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Compléments de chargement d'un trajet")
    public Flux<TripMissionSubmissionEntity> listSubmissions(@PathVariable UUID id, Authentication auth) {
        return tripUseCase.listSubmissions(id, getUserId(auth));
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @PostMapping("/return")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Enregistrer le retour d'un trajet par son code")
    public Mono<Trip> registerReturn(
        @Valid @RequestBody RegisterReturnRequest req,
        Authentication auth
    ) {
        List<ManageTripUseCase.ReturnDetailInput> detailUpdates =
            req.detailUpdates() != null
                ? req
                      .detailUpdates()
                      .stream()
                      .map(d ->
                          new ManageTripUseCase.ReturnDetailInput(
                              d.detailId(),
                              d.returnQuantity()
                          )
                      )
                      .toList()
                : List.of();

        return tripUseCase.registerReturn(
            new ManageTripUseCase.RegisterReturnCommand(
                req.tripCode(),
                req.returnDate(),
                req.returnTime(),
                req.returnLocation(),
                req.returnLat(),
                req.returnLng(),
                req.returnKmIndex(),
                req.returnFuelIndex(),
                detailUpdates
            ),
            getUserId(auth)
        );
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @GetMapping("/code/{code}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Retrouver un trajet par son code (TRJ-2026-XXXX)")
    public Mono<Trip> getByCode(@PathVariable String code, Authentication auth) {
        return tripUseCase.getTripByCode(code, getUserId(auth));
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @GetMapping("/open")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Trajets non clôturés (départ enregistré, retour en attente)")
    public Flux<Trip> listOpen(Authentication auth) {
        return tripUseCase.getOpenTrips(getUserId(auth));
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Modifier un trajet non clôturé")
    public Mono<Trip> updateTrip(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateTripRequest req,
        Authentication auth
    ) {
        return tripUseCase.updateTrip(
            id,
            new ManageTripUseCase.UpdateTripCommand(
                getUserId(auth),
                req.vehicleId(),
                req.driverId(),
                req.startDate(),
                req.startTime(),
                req.departureLocation(),
                req.departureLat(),
                req.departureLng(),
                req.departureKmIndex(),
                req.departureFuelIndex(),
                req.missionObject(),
                req.destinationLocation(),
                req.destinationLat(),
                req.destinationLng(),
                req.scheduledReturnDatetime()
            )
        );
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @PatchMapping("/{id}/vehicle")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Changer le véhicule d'un trajet non clôturé")
    public Mono<Trip> updateVehicle(
        @PathVariable UUID id,
        @RequestBody UpdateVehicleRequest req,
        Authentication auth
    ) {
        return tripUseCase.updateTripVehicle(
            id,
            req.newVehicleId(),
            getUserId(auth)
        );
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @PatchMapping("/{id}/driver")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Changer le conducteur d'un trajet planifié")
    public Mono<Trip> updateDriver(
        @PathVariable UUID id,
        @RequestBody UpdateDriverRequest req,
        Authentication auth
    ) {
        return tripUseCase.updateTripDriver(
            id,
            req.newDriverId(),
            getUserId(auth)
        );
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Annuler un trajet")
    public Mono<Trip> cancel(
        @PathVariable UUID id,
        @RequestBody(required = false) CancelTripRequest req,
        Authentication auth
    ) {
        String reason = req != null ? req.reason() : null;
        return tripUseCase.cancelTrip(id, reason, getUserId(auth));
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Détail d'un trajet")
    public Mono<Trip> getById(@PathVariable UUID id, Authentication auth) {
        return tripUseCase.getTripById(id, getUserId(auth));
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Supprimer un trajet (SCHEDULED/CANCELLED)")
    public Mono<Void> deleteTrip(@PathVariable UUID id, Authentication auth) {
        return tripUseCase.deleteTrip(id, getUserId(auth));
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @GetMapping
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(
        summary = "Lister les trajets du manager (filtres fleetId/status/vehicleId)"
    )
    public Flux<Trip> list(
        Authentication auth,
        @RequestParam(required = false) UUID fleetId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID vehicleId
    ) {
        return tripUseCase.getManagerTrips(getUserId(auth), fleetId, status, vehicleId);
    }

    // ── TÉLÉMÉTRIE CHAUFFEUR (conservée) ─────────────────────────────────────

    @Tag(name = OpenApiConfig.TAG_TRIPS_OPS)
    @PostMapping("/{id}/submissions")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Soumettre un complément d'information mission (conducteur)")
    public Mono<UUID> submitComplement(
        @PathVariable UUID id,
        @Valid @RequestBody MissionSubmissionRequest req,
        Authentication auth
    ) {
        return tripUseCase.submitMissionComplement(
            id,
            getUserId(auth),
            new ManageTripUseCase.MissionSubmissionInput(
                req.changeType(),
                req.detailId(),
                req.itemType(),
                req.description(),
                req.quantity(),
                req.weight(),
                req.notes(),
                req.stopId()
            )
        );
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @PatchMapping("/submissions/{submissionId}/approve")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Valider un complément conducteur")
    public Mono<Trip> approveSubmission(
        @PathVariable UUID submissionId,
        Authentication auth
    ) {
        return tripUseCase.approveMissionSubmission(
            submissionId,
            getUserId(auth)
        );
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @PatchMapping("/submissions/{submissionId}/reject")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Rejeter un complément conducteur")
    public Mono<Void> rejectSubmission(
        @PathVariable UUID submissionId,
        @RequestBody(required = false) RejectSubmissionRequest req,
        Authentication auth
    ) {
        return tripUseCase.rejectMissionSubmission(
            submissionId,
            req != null ? req.reason() : null,
            getUserId(auth)
        );
    }

    public record RejectSubmissionRequest(String reason) {}

    @Tag(name = OpenApiConfig.TAG_TRIPS_OPS)
    @PostMapping("/{id}/telemetry")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @Operation(summary = "Partager sa position GPS pendant le trajet en cours (un point toutes les ~30 s ; "
        + "les points à moins de 15 s du précédent sont ignorés : accepted=false)")
    public Mono<TripTracking.TelemetryAck> telemetry(
        @PathVariable UUID id,
        @RequestBody TelemetryRequest r,
        Authentication auth
    ) {
        return trackingUseCase.sendTelemetry(id, getUserId(auth), r.lat(), r.lng(), r.speed(), r.accuracy());
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @GetMapping("/{id}/positions")
    @PreAuthorize("hasAnyRole('FLEET_DRIVER', 'FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Suivi en direct d'un trajet : dernière position connue et tracé récent (du plus ancien au plus récent)")
    public Mono<TripTracking.TripPositions> positions(
        @PathVariable UUID id,
        @RequestParam(required = false) Integer limit,
        Authentication auth
    ) {
        return trackingUseCase.getPositions(id, getUserId(auth), limit);
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @GetMapping("/live-positions")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Suivi en direct : dernière position et tracé récent de tous les trajets en cours des flottes gérées")
    public Flux<TripTracking.LiveTripPosition> livePositions(
        @RequestParam(required = false) UUID fleetId,
        Authentication auth
    ) {
        return trackingUseCase.getLivePositions(getUserId(auth), fleetId);
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_OPS)
    @GetMapping("/my-active")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @Operation(summary = "Trajet actif du chauffeur (lecture seule)")
    public Mono<Trip> getMyActive(Authentication auth) {
        return tripUseCase.getMyActiveTrip(getUserId(auth));
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_OPS)
    @GetMapping("/my-history")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @Operation(summary = "Historique du chauffeur")
    public Flux<Trip> getMyHistory(Authentication auth) {
        return tripUseCase.getMyTripHistory(getUserId(auth));
    }
}
