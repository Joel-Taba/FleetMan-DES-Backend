package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.DriverAbsenceRequestService;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Demandes d'absence : le chauffeur demande ou retire, le gestionnaire de sa flotte accepte ou refuse. */
@RestController
@RequestMapping("/api/v1/drivers/{driverId}/absence-requests")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_DRIVERS)
@SecurityRequirement(name = "bearerAuth")
public class DriverAbsenceRequestController {

    private static final String STAFF = "hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')";

    private final DriverAbsenceRequestService requests;

    private static UUID userId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    public record RejectRequest(String reason) {}

    @GetMapping
    @PreAuthorize("hasAnyRole('FLEET_DRIVER', 'FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Demandes d'absence d'un chauffeur",
            description = "Le chauffeur voit les siennes, le gestionnaire celles de ses chauffeurs ; "
                    + "conflicts = postes et trajets prévus sur les jours demandés (demandes en attente).")
    public Flux<DriverAbsenceRequestService.AbsenceRequest> list(@PathVariable UUID driverId, Authentication auth) {
        return requests.list(driverId, userId(auth));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @Operation(summary = "Demander une absence (congé ou arrêt maladie)",
            description = "Le gestionnaire de la flotte est notifié (ABSENCE_REQUESTED).")
    public Mono<DriverAbsenceRequestService.AbsenceRequest> create(@PathVariable UUID driverId,
                                                                   @RequestBody DriverAbsenceRequestService.CreateCommand body,
                                                                   Authentication auth) {
        return requests.create(driverId, userId(auth), body);
    }

    @PostMapping("/{requestId}/withdraw")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @Operation(summary = "Retirer sa demande encore en attente")
    public Mono<DriverAbsenceRequestService.AbsenceRequest> withdraw(@PathVariable UUID driverId, @PathVariable UUID requestId,
                                                                     Authentication auth) {
        return requests.withdraw(driverId, requestId, userId(auth));
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize(STAFF)
    @Operation(summary = "Accepter une demande : l'absence est enregistrée",
            description = "Refusé si des postes ou trajets sont encore prévus sur ces jours (réaffectez-les d'abord).")
    public Mono<DriverAbsenceRequestService.AbsenceRequest> approve(@PathVariable UUID driverId, @PathVariable UUID requestId,
                                                                    Authentication auth) {
        return requests.approve(driverId, requestId, userId(auth));
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize(STAFF)
    @Operation(summary = "Refuser une demande (motif obligatoire, transmis au chauffeur)")
    public Mono<DriverAbsenceRequestService.AbsenceRequest> reject(@PathVariable UUID driverId, @PathVariable UUID requestId,
                                                                   @RequestBody(required = false) RejectRequest body,
                                                                   Authentication auth) {
        return requests.reject(driverId, requestId, userId(auth), body != null ? body.reason() : null);
    }
}
