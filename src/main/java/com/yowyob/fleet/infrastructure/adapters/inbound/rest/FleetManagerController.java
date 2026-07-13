package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.in.ManageFleetManagerUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetManagerResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerKpiResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.UpdateManagerRequest;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fleet-managers")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_FLEET_MANAGERS)
@SecurityRequirement(name = "bearerAuth")
public class FleetManagerController {

    private final ManageFleetManagerUseCase managerUseCase;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    private boolean checkAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(ga -> ga.getAuthority().equals("ROLE_FLEET_ADMIN") ||
                ga.getAuthority().equals("ROLE_FLEET_SUPER_ADMIN"));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Détails de mon entreprise", description = "Profil complet incluant le nombre réel de flottes.")
    public Mono<FleetManagerResponse> getMyManagerProfile(
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            Authentication auth) {
        return managerUseCase.getManagerDetails(getUserId(auth), token);
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Tableau de bord (KPIs)", description = "Statistiques clés pour l'écran d'accueil du manager.")
    public Mono<ManagerKpiResponse> getMyKpis(Authentication auth) {
        return managerUseCase.getManagerKpis(getUserId(auth), checkAdmin(auth));
    }

    @PutMapping("/me/company")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Mettre à jour les informations de mon entreprise")
    public Mono<Void> updateMyCompany(@Valid @RequestBody UpdateManagerRequest request, Authentication auth) {
        return managerUseCase.updateManagerCompany(
                getUserId(auth),
                request.companyName(),
                request.phone(),
                request.address(),
                request.city(),
                request.logoUrl());
    }
}