package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.Trip;
import com.yowyob.fleet.domain.ports.in.ManageTripUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.TelemetryRequest;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record TripDetailInputDto(
            @NotBlank String itemType,
            String description,
            @NotNull int quantity,
            BigDecimal weight,
            Integer departureQuantity) {
    }

    public record CreateTripRequest(
            @NotNull UUID vehicleId,
            @NotNull UUID driverId,
            @NotNull UUID fleetId,
            @NotNull LocalDate startDate,
            @NotNull LocalTime startTime,
            String departureLocation,
            BigDecimal departureKmIndex,
            BigDecimal departureFuelIndex,
            String missionObject,
            BigDecimal missionCost,
            String rateType,
            LocalDateTime scheduledReturnDatetime,
            List<TripDetailInputDto> details) {
    }

    public record ReturnDetailInputDto(UUID detailId, Integer returnQuantity) {
    }

    public record RegisterReturnRequest(
            @NotBlank String tripCode,
            @NotNull LocalDate returnDate,
            @NotNull LocalTime returnTime,
            String returnLocation,
            BigDecimal returnKmIndex,
            BigDecimal returnFuelIndex,
            List<ReturnDetailInputDto> detailUpdates) {
    }

    public record UpdateDriverRequest(@NotNull UUID newDriverId) {
    }

    public record CancelTripRequest(String reason) {
    }

    // ── OPÉRATIONS MANAGER ───────────────────────────────────────────────────

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @PostMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un trajet (départ) — réservé au Fleet Manager")
    public Mono<Trip> createTrip(
            @Valid @RequestBody CreateTripRequest req,
            Authentication auth) {
        List<ManageTripUseCase.TripDetailInput> details = req.details() != null
                ? req
                        .details()
                        .stream()
                        .map(d -> new ManageTripUseCase.TripDetailInput(
                                d.itemType(),
                                d.description(),
                                d.quantity(),
                                d.weight(),
                                d.departureQuantity()))
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
                        req.departureKmIndex(),
                        req.departureFuelIndex(),
                        req.missionObject(),
                        req.missionCost(),
                        req.rateType(),
                        req.scheduledReturnDatetime(),
                        details));
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @PostMapping("/return")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Enregistrer le retour d'un trajet par son code")
    public Mono<Trip> registerReturn(
            @Valid @RequestBody RegisterReturnRequest req) {
        List<ManageTripUseCase.ReturnDetailInput> detailUpdates = req.detailUpdates() != null
                ? req
                        .detailUpdates()
                        .stream()
                        .map(d -> new ManageTripUseCase.ReturnDetailInput(
                                d.detailId(),
                                d.returnQuantity()))
                        .toList()
                : List.of();

        return tripUseCase.registerReturn(
                new ManageTripUseCase.RegisterReturnCommand(
                        req.tripCode(),
                        req.returnDate(),
                        req.returnTime(),
                        req.returnLocation(),
                        req.returnKmIndex(),
                        req.returnFuelIndex(),
                        detailUpdates));
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @GetMapping("/code/{code}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Retrouver un trajet par son code (TRJ-2026-XXXX)")
    public Mono<Trip> getByCode(@PathVariable String code) {
        return tripUseCase.getTripByCode(code);
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @PatchMapping("/{id}/driver")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Changer le conducteur d'un trajet planifié")
    public Mono<Trip> updateDriver(
            @PathVariable UUID id,
            @RequestBody UpdateDriverRequest req,
            Authentication auth) {
        return tripUseCase.updateTripDriver(
                id,
                req.newDriverId(),
                getUserId(auth));
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Annuler un trajet")
    public Mono<Trip> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelTripRequest req,
            Authentication auth) {
        String reason = req != null ? req.reason() : null;
        return tripUseCase.cancelTrip(id, reason, getUserId(auth));
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Détail d'un trajet")
    public Mono<Trip> getById(@PathVariable UUID id) {
        return tripUseCase.getTripById(id);
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @GetMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Lister les trajets du manager (filtre optionnel par flotte)")
    public Flux<Trip> list(
            Authentication auth,
            @RequestParam(required = false) UUID fleetId) {
        return tripUseCase.getManagerTrips(getUserId(auth), fleetId);
    }

    // ── TÉLÉMÉTRIE CHAUFFEUR (conservée) ─────────────────────────────────────

    @Tag(name = OpenApiConfig.TAG_TRIPS_OPS)
    @PostMapping("/{id}/telemetry")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @Operation(summary = "Envoyer un point de télémétrie GPS")
    public Mono<Void> telemetry(
            @PathVariable UUID id,
            @RequestBody TelemetryRequest r) {
        return tripUseCase.sendTelemetry(id, r.lat(), r.lng(), r.speed());
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

    public record StartTripDto(
            BigDecimal departureKmIndex,
            BigDecimal departureFuelIndex,
            String departureLocation) {
    }

    public record CompleteTripDto(
            BigDecimal returnKmIndex,
            BigDecimal returnFuelIndex,
            String returnLocation) {
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_OPS)
    @PutMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('FLEET_DRIVER', 'FLEET_MANAGER')")
    @Operation(summary = "Démarrer le trajet")
    public Mono<Trip> startTrip(
            @PathVariable UUID id,
            @RequestBody StartTripDto req) {
        return tripUseCase.startTrip(id, req.departureKmIndex(), req.departureFuelIndex(), req.departureLocation());
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_OPS)
    @PutMapping("/{id}/returning")
    @PreAuthorize("hasAnyRole('FLEET_DRIVER', 'FLEET_MANAGER')")
    @Operation(summary = "Marquer le trajet comme retour en cours")
    public Mono<Trip> returningTrip(
            @PathVariable UUID id) {
        return tripUseCase.returningTrip(id);
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_OPS)
    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('FLEET_DRIVER', 'FLEET_MANAGER')")
    @Operation(summary = "Terminer le trajet")
    public Mono<Trip> completeTrip(
            @PathVariable UUID id,
            @RequestBody CompleteTripDto req) {
        return tripUseCase.completeTrip(id, req.returnKmIndex(), req.returnFuelIndex(), req.returnLocation());
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Annuler un trajet (alias PUT)")
    public Mono<Trip> cancelPut(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelTripRequest req,
            Authentication auth) {
        return cancel(id, req, auth);
    }
}
