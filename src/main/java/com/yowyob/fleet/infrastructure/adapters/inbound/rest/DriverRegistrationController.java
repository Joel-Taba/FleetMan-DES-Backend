package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.DriverRegistrationService;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/** Demandes d'inscription des chauffeurs, traitées par le gestionnaire de la flotte. */
@RestController
@RequestMapping("/api/v1/driver-registrations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FLEET_MANAGER')")
@Tag(name = "Inscription chauffeur", description = "Validation des demandes et codes d'activation")
public class DriverRegistrationController {

    public record RejectRequest(String reason) {}

    private final DriverRegistrationService registrations;

    private static UUID userId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    @GetMapping
    @Operation(summary = "Demandes de mes flottes (en cours et récentes)")
    public Flux<DriverRegistrationService.Registration> list(Authentication auth) {
        return registrations.list(userId(auth));
    }

    @GetMapping("/pending-count")
    @Operation(summary = "Nombre de demandes en attente de décision")
    public Mono<Map<String, Long>> pendingCount(Authentication auth) {
        return registrations.pendingCount(userId(auth)).map(n -> Map.of("count", n));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Accepter : génère le code d'activation (72 h, 5 essais)")
    public Mono<DriverRegistrationService.Registration> approve(@PathVariable UUID id, Authentication auth) {
        return registrations.approve(userId(auth), id);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Refuser avec un motif")
    public Mono<DriverRegistrationService.Registration> reject(@PathVariable UUID id, @RequestBody RejectRequest req,
                                                               Authentication auth) {
        return registrations.reject(userId(auth), id, req.reason());
    }

    @PostMapping("/{id}/send-code")
    @Operation(summary = "Envoyer le code par e-mail et/ou SMS selon les coordonnées du chauffeur")
    public Mono<DriverRegistrationService.SendResult> sendCode(@PathVariable UUID id, Authentication auth) {
        return registrations.sendCode(userId(auth), id);
    }

    @PostMapping("/{id}/regenerate-code")
    @Operation(summary = "Générer un nouveau code (l'ancien ne fonctionne plus)")
    public Mono<DriverRegistrationService.Registration> regenerateCode(@PathVariable UUID id, Authentication auth) {
        return registrations.regenerateCode(userId(auth), id);
    }

    @GetMapping("/fleet-codes")
    @Operation(summary = "Code d'inscription de chacune de mes flottes, à communiquer aux nouveaux chauffeurs")
    public Flux<DriverRegistrationService.FleetCode> fleetCodes(Authentication auth) {
        return registrations.fleetCodes(userId(auth));
    }

    @PostMapping("/fleet-codes/{fleetId}/regenerate")
    @Operation(summary = "Changer le code d'inscription d'une flotte (l'ancien ne fonctionne plus)")
    public Mono<DriverRegistrationService.FleetCode> regenerateFleetCode(@PathVariable UUID fleetId, Authentication auth) {
        return registrations.regenerateFleetCode(userId(auth), fleetId);
    }
}
