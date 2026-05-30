package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.Trip;
import com.yowyob.fleet.domain.ports.in.ManageTripUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.StartTripRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.TelemetryRequest;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TripController {

    private final ManageTripUseCase tripUseCase;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    // --- 11a. CHAUFFEUR ---
    @Tag(name = OpenApiConfig.TAG_TRIPS_OPS)
    @PostMapping("/start")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Trip> start(@RequestBody StartTripRequest r, Authentication a) {
        return tripUseCase.startTrip(getUserId(a), r.vehicleId());
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_OPS)
    @PostMapping("/{id}/telemetry")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    public Mono<Void> telemetry(@PathVariable UUID id, @RequestBody TelemetryRequest r) {
        return tripUseCase.sendTelemetry(id, r.lat(), r.lng(), r.speed());
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_OPS)
    @PostMapping("/{id}/end")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    public Mono<Trip> end(@PathVariable UUID id) {
        return tripUseCase.endTrip(id);
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_OPS)
    @GetMapping("/my-active")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    public Mono<Trip> getMyActive(Authentication a) {
        return tripUseCase.getMyActiveTrip(getUserId(a));
    }

    // --- 11b. MANAGER ---
    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    public Mono<Trip> getById(@PathVariable UUID id) {
        return tripUseCase.getTripById(id);
    }

    @Tag(name = OpenApiConfig.TAG_TRIPS_MGT)
    @GetMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    public Flux<Trip> list(Authentication a, @RequestParam(required = false) UUID fleetId) {
        return tripUseCase.getManagerTrips(getUserId(a), fleetId);
    }
}