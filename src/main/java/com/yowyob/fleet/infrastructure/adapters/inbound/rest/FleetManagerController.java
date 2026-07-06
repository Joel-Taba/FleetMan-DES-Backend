package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.in.ManageFleetManagerUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.application.service.PlanLimitGuard;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetManagerResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerSubscriptionResponse;
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
    private final PlanLimitGuard planLimitGuard;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Détails de mon entreprise", description = "Profil complet incluant le nombre réel de flottes.")
    public Mono<FleetManagerResponse> getMyManagerProfile(
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            Authentication auth
    ) {
        return managerUseCase.getManagerDetails(getUserId(auth), token);
    }

    @GetMapping("/me/subscription")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Mon abonnement et limites de plan")
    public Mono<ManagerSubscriptionResponse> getMySubscription(Authentication auth) {
        return planLimitGuard.getSubscription(getUserId(auth));
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Tableau de bord (KPIs)", description = "Statistiques clés pour l'écran d'accueil du manager.")
    public Mono<ManagerKpiResponse> getMyKpis(Authentication auth) {
        return managerUseCase.getManagerKpis(getUserId(auth));
    }

    @PutMapping("/me/company")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Mettre à jour mon nom d'entreprise")
    public Mono<Void> updateMyCompany(@Valid @RequestBody UpdateManagerRequest request, Authentication auth) {
        return managerUseCase.updateManagerCompany(getUserId(auth), request.companyName());
    }
}