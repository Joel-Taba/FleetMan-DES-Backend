package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.in.ManageAdminUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/management")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_ADMIN_MANAGERS)
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
public class AdminManagerController {

    private final ManageAdminUseCase adminUseCase;

    private boolean isSuper(Authentication a) {
        return a.getAuthorities().stream().anyMatch(ga -> ga.getAuthority().equals("ROLE_FLEET_SUPER_ADMIN"));
    }

    @GetMapping("/managers")
    @PreAuthorize("hasAnyRole('FLEET_ADMIN', 'FLEET_SUPER_ADMIN', 'FLEET_MANAGER')")
    @Operation(summary = "Lister les Fleet Managers")
    public Flux<AuthPort.UserDetail> list(
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String t) {
        return adminUseCase.listFleetManagers(t);
    }

    @GetMapping("/managers/{id}")
    @Operation(summary = "Détails d'un Fleet Manager")
    public Mono<AuthPort.UserDetail> getOne(@PathVariable UUID id,
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String t, Authentication auth) {
        return adminUseCase.getManagerDetails(id, t, isSuper(auth));
    }

    @PatchMapping("/managers/{id}/toggle")
    @Operation(summary = "Activer/Désactiver un Fleet Manager")
    public Mono<Void> toggle(@PathVariable UUID id, Authentication auth) {
        AuthPort.UserDetail currentUser = (AuthPort.UserDetail) auth.getPrincipal();
        return adminUseCase.toggleManagerStatus(id, currentUser.id(), isSuper(auth));
    }
}