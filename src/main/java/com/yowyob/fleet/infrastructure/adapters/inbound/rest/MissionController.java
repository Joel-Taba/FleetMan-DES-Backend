package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.Mission;
import com.yowyob.fleet.domain.ports.in.ManageMissionUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.MissionRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.MissionResponse;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_MISSIONS)
@SecurityRequirement(name = "bearerAuth")
public class MissionController {

    private final ManageMissionUseCase missionUseCase;

    private UUID getUserId(Authentication auth) {
        if (auth.getPrincipal() instanceof AuthPort.UserDetail userDetail) {
            return userDetail.id();
        }
        return UUID.fromString(auth.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Planifier une nouvelle mission",
               description = "Crée une mission liant un véhicule, un conducteur et optionnellement une livraison TiiBnTick. " +
                             "Vérifie qu'aucun chevauchement d'affectation n'existe et que le véhicule n'est pas en maintenance.")
    public Mono<MissionResponse> create(
            @Valid @RequestBody MissionRequest request,
            Authentication auth) {

        UUID managerId = getUserId(auth);

        ManageMissionUseCase.CreateMissionCommand cmd = new ManageMissionUseCase.CreateMissionCommand(
                request.vehicleId(),
                request.driverId(),
                managerId,
                request.type(),
                request.scheduledStart(),
                request.scheduledEnd(),
                request.startLocationId(),
                request.endLocationId(),
                request.tiiBnTickDeliveryId(),
                request.tiiBnTickParcelId(),
                request.geofenceObjectId(),
                request.notes(),
                null // tenantId résolu ultérieurement ou via contexte
        );

        return missionUseCase.createMission(cmd).map(MissionResponse::fromDomain);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Détail d'une mission", description = "Retourne l'état courant et la traçabilité complète de la mission.")
    public Mono<MissionResponse> getById(@PathVariable UUID id) {
        return missionUseCase.getById(id).map(MissionResponse::fromDomain);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Lister les missions", description = "Retourne toutes les missions du gestionnaire connecté.")
    public Flux<MissionResponse> list(
            @RequestParam(required = false) Mission.Status status,
            Authentication auth) {
        UUID managerId = getUserId(auth);
        if (status != null) {
            return missionUseCase.getMissionsByStatus(status, managerId).map(MissionResponse::fromDomain);
        }
        return missionUseCase.getMissionsByManager(managerId).map(MissionResponse::fromDomain);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('FLEET_DRIVER', 'FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Démarrer une mission", description = "Marque la mission comme ACTIVE et enregistre l'horodatage effectif de départ.")
    public Mono<MissionResponse> start(
            @PathVariable UUID id,
            Authentication auth) {
        UUID driverId = getUserId(auth);
        return missionUseCase.startMission(id, driverId).map(MissionResponse::fromDomain);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('FLEET_DRIVER', 'FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Clôturer une mission",
               description = "Clôture la mission (COMPLETED). Exige qu'une preuve de livraison soit préalablement enregistrée. Notifie le service TiiBnTick.")
    public Mono<MissionResponse> complete(
            @PathVariable UUID id,
            Authentication auth) {
        UUID driverId = getUserId(auth);
        return missionUseCase.completeMission(id, driverId).map(MissionResponse::fromDomain);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Annuler une mission", description = "Passe la mission en statut CANCELLED avec motif obligatoire.")
    public Mono<MissionResponse> cancel(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        String reason = body.getOrDefault("reason", "Annulation opérateur");
        UUID operatorId = getUserId(auth);
        return missionUseCase.cancelMission(id, reason, operatorId).map(MissionResponse::fromDomain);
    }

    @PostMapping("/{id}/link-delivery")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Lier une livraison TiiBnTick",
               description = "Associe une commande externe TiiBnTick (deliveryId + parcelId QR) à cette mission.")
    public Mono<MissionResponse> linkDelivery(
            @PathVariable UUID id,
            @RequestParam UUID deliveryId,
            @RequestParam(required = false) String parcelId) {
        return missionUseCase.linkToTiiBnTickDelivery(id, deliveryId, parcelId).map(MissionResponse::fromDomain);
    }
}

