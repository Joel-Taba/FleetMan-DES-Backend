package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.TripAvailabilityService;
import com.yowyob.fleet.application.service.TripStopService;
import com.yowyob.fleet.domain.exception.TripException;
import com.yowyob.fleet.domain.model.TripStop;
import com.yowyob.fleet.domain.ports.in.ManageTripUseCase;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import com.yowyob.fleet.infrastructure.config.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Tournée multi-arrêts d'un trajet et ressources disponibles au planning. */
@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_TRIPS_OPS)
@SecurityRequirement(name = "bearerAuth")
public class TripStopController {

    private static final String MANAGERS = "hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')";
    private static final String CREW = "hasAnyRole('FLEET_DRIVER', 'FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')";

    private final TripStopService stops;
    private final TripAvailabilityService availability;

    public record NewStopRequest(@Valid @NotNull TripController.TripStopRequest stop, Integer position,
                                 List<TripController.TripDetailInputDto> lines) {}

    public record ReorderRequest(@NotNull List<UUID> stopIds) {}

    public record ArriveRequest(LocalDateTime arrivedAt) {}

    public record HandledLineRequest(@NotNull UUID detailId, Integer quantity, String shortageReason) {}

    public record CompleteRequest(LocalDateTime arrivedAt, LocalDateTime completedAt, String recipientName,
                                  String proofUrl, String notes, List<HandledLineRequest> lines) {}

    public record FailRequest(String reason, String notes) {}

    public record SkipRequest(String reason) {}

    /** Ligne du manifeste rattachée à un arrêt (modifiable avant le départ). */
    public record ManifestLineRequest(UUID stopId, @jakarta.validation.constraints.NotBlank String itemType,
                                      String description, @NotNull Integer quantity,
                                      java.math.BigDecimal weight, Integer departureQuantity) {
        ManageTripUseCase.TripDetailInput toInput() {
            return new ManageTripUseCase.TripDetailInput(itemType, description, quantity, weight, departureQuantity, null);
        }
    }

    @GetMapping("/availability")
    @PreAuthorize(MANAGERS)
    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @Operation(summary = "Véhicules et chauffeurs libres sur un créneau de trajet",
            description = "Seules les ressources engageables sont renvoyées (état, conformité, absences, permis, "
                    + "trajets et postes qui se chevauchent). vehicleId / driverId restreignent l'autre liste au binôme possible.")
    public Mono<TripAvailabilityService.Availability> availability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledReturn,
            @RequestParam(required = false) UUID fleetId,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) UUID excludeTripId,
            Authentication auth
    ) {
        LocalDateTime start = LocalDateTime.of(startDate, startTime);
        if (!scheduledReturn.isAfter(start)) {
            return Mono.error(TripException.invalidSchedule());
        }
        return availability.find(AuthPrincipal.userId(auth),
                new TripAvailabilityService.Query(start, scheduledReturn, fleetId, vehicleId, driverId, excludeTripId));
    }

    @GetMapping("/{tripId}/stops")
    @PreAuthorize(CREW)
    @Operation(summary = "Arrêts de la tournée, dans l'ordre de passage")
    public Flux<TripStop> list(@PathVariable UUID tripId, Authentication auth) {
        return stops.list(tripId, AuthPrincipal.userId(auth));
    }

    @PostMapping("/{tripId}/stops")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Ajouter un arrêt (avant le départ ou en route)")
    public Mono<TripStop> add(@PathVariable UUID tripId, @Valid @RequestBody NewStopRequest req, Authentication auth) {
        List<ManageTripUseCase.TripDetailInput> lines = req.lines() != null
                ? req.lines().stream().map(d -> new ManageTripUseCase.TripDetailInput(
                        d.itemType(), d.description(), d.quantity(), d.weight(), d.departureQuantity(), null)).toList()
                : List.of();
        return stops.add(tripId, AuthPrincipal.userId(auth),
                new TripStopService.NewStopCommand(req.stop().toInput(), req.position(), lines));
    }

    @PutMapping("/{tripId}/stops/{stopId}")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Modifier un arrêt encore en attente")
    public Mono<TripStop> update(@PathVariable UUID tripId, @PathVariable UUID stopId,
                                 @Valid @RequestBody TripController.TripStopRequest req, Authentication auth) {
        return stops.update(tripId, stopId, AuthPrincipal.userId(auth), req.toInput());
    }

    @DeleteMapping("/{tripId}/stops/{stopId}")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Retirer un arrêt avant le départ")
    public Mono<Void> delete(@PathVariable UUID tripId, @PathVariable UUID stopId, Authentication auth) {
        return stops.delete(tripId, stopId, AuthPrincipal.userId(auth));
    }

    @PutMapping("/{tripId}/stops/order")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Changer l'ordre de passage avant le départ")
    public Mono<List<TripStop>> reorder(@PathVariable UUID tripId, @Valid @RequestBody ReorderRequest req, Authentication auth) {
        return stops.reorder(tripId, AuthPrincipal.userId(auth), req.stopIds());
    }

    @PostMapping("/{tripId}/stops/{stopId}/arrive")
    @PreAuthorize(CREW)
    @Operation(summary = "Arrivée sur un arrêt")
    public Mono<TripStop> arrive(@PathVariable UUID tripId, @PathVariable UUID stopId,
                                 @RequestBody(required = false) ArriveRequest req, Authentication auth) {
        return stops.arrive(tripId, stopId, AuthPrincipal.userId(auth), req != null ? req.arrivedAt() : null);
    }

    @PostMapping("/{tripId}/stops/{stopId}/complete")
    @PreAuthorize(CREW)
    @Operation(summary = "Arrêt servi : quantités, réceptionnaire, preuve")
    public Mono<TripStop> complete(@PathVariable UUID tripId, @PathVariable UUID stopId,
                                   @RequestBody CompleteRequest req, Authentication auth) {
        List<TripStopService.HandledLine> lines = req.lines() != null
                ? req.lines().stream().map(l -> new TripStopService.HandledLine(l.detailId(), l.quantity(), l.shortageReason())).toList()
                : List.of();
        return stops.complete(tripId, stopId, AuthPrincipal.userId(auth), new TripStopService.CompleteCommand(
                req.arrivedAt(), req.completedAt(), req.recipientName(), req.proofUrl(), req.notes(), lines));
    }

    @PostMapping("/{tripId}/stops/{stopId}/fail")
    @PreAuthorize(CREW)
    @Operation(summary = "Arrêt non servi, avec motif")
    public Mono<TripStop> fail(@PathVariable UUID tripId, @PathVariable UUID stopId,
                               @RequestBody FailRequest req, Authentication auth) {
        return stops.fail(tripId, stopId, AuthPrincipal.userId(auth), req.reason(), req.notes());
    }

    @PostMapping("/{tripId}/stops/{stopId}/skip")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Passer un arrêt en route (gestionnaire)")
    public Mono<TripStop> skip(@PathVariable UUID tripId, @PathVariable UUID stopId,
                               @RequestBody SkipRequest req, Authentication auth) {
        return stops.skip(tripId, stopId, AuthPrincipal.userId(auth), req.reason());
    }

    @PostMapping("/{tripId}/details")
    @PreAuthorize(MANAGERS)
    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @Operation(summary = "Ajouter une ligne au manifeste d'un trajet planifié (stopId obligatoire)")
    public Mono<Void> addLine(@PathVariable UUID tripId, @Valid @RequestBody ManifestLineRequest req, Authentication auth) {
        if (req.stopId() == null) {
            return Mono.error(TripException.invalidManifestLine("Choisissez l'arrêt de la ligne."));
        }
        return stops.addLine(tripId, req.stopId(), AuthPrincipal.userId(auth), req.toInput());
    }

    @PutMapping("/{tripId}/details/{detailId}")
    @PreAuthorize(MANAGERS)
    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @Operation(summary = "Modifier une ligne du manifeste avant le départ")
    public Mono<Void> updateLine(@PathVariable UUID tripId, @PathVariable UUID detailId,
                                 @Valid @RequestBody ManifestLineRequest req, Authentication auth) {
        return stops.updateLine(tripId, detailId, req.stopId(), AuthPrincipal.userId(auth), req.toInput());
    }

    @DeleteMapping("/{tripId}/details/{detailId}")
    @PreAuthorize(MANAGERS)
    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @Operation(summary = "Retirer une ligne du manifeste avant le départ")
    public Mono<Void> deleteLine(@PathVariable UUID tripId, @PathVariable UUID detailId, Authentication auth) {
        return stops.deleteLine(tripId, detailId, AuthPrincipal.userId(auth));
    }
}
