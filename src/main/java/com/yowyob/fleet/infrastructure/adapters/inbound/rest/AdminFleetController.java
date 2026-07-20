package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.in.ManageFleetUseCase;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetStatsResponse;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import com.yowyob.fleet.infrastructure.mappers.FleetMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Gestion des flottes côté Administrateur : c'est l'admin qui crée les flottes
 * puis les assigne à un gestionnaire (voir AdminManagerController pour
 * l'assignation) — le gestionnaire ne crée/modifie/supprime plus ses flottes
 * lui-même, il ne fait qu'en consulter le détail (voir FleetController).
 */
@RestController
@RequestMapping("/api/v1/admin/management/fleets")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_FLEETS)
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
public class AdminFleetController {

    private final ManageFleetUseCase fleetUseCase;
    private final FleetMapper mapper;

    @GetMapping
    @Operation(summary = "Lister toutes les flottes", description = "Toutes flottes confondues, assignées ou non.")
    public Flux<FleetResponse> list() {
        return fleetUseCase.getFleets(null, true).map(mapper::toResponse);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détails d'une flotte")
    public Mono<FleetResponse> getById(@PathVariable UUID id) {
        return fleetUseCase.getFleetById(id, null, true).map(mapper::toResponse);
    }

    @GetMapping("/{id}/stats")
    @Operation(summary = "KPIs d'une flotte")
    public Mono<FleetStatsResponse> getStats(@PathVariable UUID id) {
        return fleetUseCase.getFleetStatistics(id, null, true);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une flotte", description = "managerId optionnel dans le body via /managers/{id}/fleets pour l'assignation initiale.")
    public Mono<FleetResponse> create(@Valid @RequestBody FleetRequest request) {
        return fleetUseCase.createFleetAsAdmin(mapper.toDomain(request), null)
                .map(mapper::toResponse);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une flotte")
    public Mono<FleetResponse> update(@PathVariable UUID id, @Valid @RequestBody FleetRequest request) {
        return fleetUseCase.updateFleet(id, mapper.toDomain(request), null, true)
                .map(mapper::toResponse);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer une flotte", description = "Interdit si la flotte contient encore des ressources.")
    public Mono<Void> delete(@PathVariable UUID id) {
        return fleetUseCase.deleteFleet(id, null, true);
    }
}
