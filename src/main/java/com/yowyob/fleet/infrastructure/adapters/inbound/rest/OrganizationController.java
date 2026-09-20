package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.OrganizationService;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.OrganizationDtos;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organization")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_ORGANIZATION)
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('FLEET_ADMIN', 'FLEET_SUPER_ADMIN', 'FLEET_MANAGER')")
public class OrganizationController {

    private final OrganizationService organizationService;

    @Parameter(name = "adminId", description = "UUID de l'admin (organisation). Optionnel : déduit du JWT. Super-admin : peut viser une autre org.")
    @GetMapping("/summary")
    @Operation(summary = "Compteurs",
            description = "Admin : organisation entière. Manager : ses flottes affectées.")
    public Mono<OrganizationDtos.OrganizationSummary> summary(
            @RequestParam(required = false) UUID adminId,
            Authentication auth
    ) {
        return organizationService.resolveOrganizationAdminId(auth, adminId)
                .flatMap(orgId -> organizationService.scopedManagerId(auth, null)
                        .flatMap(mid -> organizationService.summary(orgId, mid))
                        .switchIfEmpty(organizationService.summary(orgId, null)));
    }

    @GetMapping("/kpis")
    @Operation(summary = "Bilan",
            description = "Admin : organisation entière. Manager : ses flottes affectées.")
    public Mono<OrganizationDtos.OrganizationKpis> kpis(
            @RequestParam(required = false) UUID adminId,
            Authentication auth
    ) {
        return organizationService.resolveOrganizationAdminId(auth, adminId)
                .flatMap(orgId -> organizationService.scopedManagerId(auth, null)
                        .flatMap(mid -> organizationService.kpis(orgId, mid))
                        .switchIfEmpty(organizationService.kpis(orgId, null)));
    }

    @GetMapping("/profile")
    @Operation(summary = "Profil de l'organisation (lecture). Modification réservée à l'admin.")
    public Mono<OrganizationDtos.OrganizationProfile> profile(
            @RequestParam(required = false) UUID adminId,
            Authentication auth
    ) {
        return organizationService.resolveOrganizationAdminId(auth, adminId)
                .flatMap(organizationService::profile);
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAnyRole('FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Mettre à jour le nom / logo de l'organisation")
    public Mono<OrganizationDtos.OrganizationProfile> updateProfile(
            @RequestBody UpdateOrganizationProfileRequest request,
            Authentication auth
    ) {
        return organizationService.resolveOrganizationAdminId(auth, null)
                .flatMap(id -> organizationService.updateProfile(id, request.organizationName(), request.logoUrl()));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Changer le logo de l'organisation")
    public Mono<OrganizationDtos.OrganizationProfile> uploadLogo(
            @RequestPart("file") Part filePart,
            Authentication auth
    ) {
        String filename = (filePart instanceof FilePart file) ? file.filename() : "logo.jpg";
        String contentType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "image/jpeg";
        return organizationService.resolveOrganizationAdminId(auth, null)
                .flatMap(adminId -> DataBufferUtils.join(filePart.content())
                        .map(buffer -> {
                            byte[] bytes = new byte[buffer.readableByteCount()];
                            buffer.read(bytes);
                            DataBufferUtils.release(buffer);
                            return bytes;
                        })
                        .flatMap(bytes -> organizationService.updateLogo(adminId, bytes, filename, contentType)));
    }

    public record UpdateOrganizationProfileRequest(String organizationName, String logoUrl) {}

    @GetMapping("/fleets")
    @Operation(summary = "Toutes les flottes de l'organisation")
    public Flux<FleetResponse> fleets(
            @RequestParam(required = false) UUID adminId,
            Authentication auth
    ) {
        return organizationService.resolveOrganizationAdminId(auth, adminId)
                .flatMapMany(orgId -> organizationService.scopedManagerId(auth, null)
                        .flatMapMany(mid -> organizationService.fleets(orgId, mid))
                        .switchIfEmpty(organizationService.fleets(orgId, null)));
    }

    @GetMapping("/vehicles")
    @Operation(summary = "Véhicules",
            description = "Admin : toute l'organisation. Manager : véhicules de ses flottes.")
    public Flux<OrganizationDtos.OrganizationVehicle> vehicles(
            @RequestParam(required = false) UUID adminId,
            Authentication auth
    ) {
        return organizationService.resolveOrganizationAdminId(auth, adminId)
                .flatMapMany(orgId -> organizationService.scopedManagerId(auth, null)
                        .flatMapMany(mid -> organizationService.vehicles(orgId, mid))
                        .switchIfEmpty(organizationService.vehicles(orgId, null)));
    }

    @GetMapping("/vehicles/recent")
    @Operation(summary = "Véhicules récemment manipulés de l'administrateur connecté",
            description = "Retourne les véhicules de l'organisation triés par dernière mise à jour (updated_at).")
    public Flux<OrganizationDtos.OrganizationVehicle> recentVehicles(
            @RequestParam(required = false) UUID adminId,
            @RequestParam(defaultValue = "10") int limit,
            Authentication auth
    ) {
        return organizationService.resolveOrganizationAdminId(auth, adminId)
                .flatMapMany(id -> organizationService.recentVehicles(id, limit));
    }

    @GetMapping("/drivers")
    @Operation(summary = "Tous les chauffeurs de l'organisation")
    public Flux<DriverResponse> drivers(
            @RequestParam(required = false) UUID adminId,
            Authentication auth
    ) {
        return organizationService.resolveOrganizationAdminId(auth, adminId)
                .flatMapMany(orgId -> organizationService.scopedManagerId(auth, null)
                        .flatMapMany(mid -> organizationService.drivers(orgId, mid))
                        .switchIfEmpty(organizationService.drivers(orgId, null)));
    }

    @GetMapping("/managers")
    @Operation(summary = "Tous les managers de l'organisation")
    public Flux<OrganizationDtos.AuthManager> managers(
            @RequestParam(required = false) UUID adminId,
            Authentication auth
    ) {
        return organizationService.resolveOrganizationAdminId(auth, adminId)
                .flatMapMany(organizationService::managers);
    }

    @GetMapping("/people")
    @Operation(summary = "Chauffeurs et managers de l'organisation")
    public Mono<OrganizationDtos.OrganizationPeople> people(
            @RequestParam(required = false) UUID adminId,
            Authentication auth
    ) {
        return organizationService.resolveOrganizationAdminId(auth, adminId)
                .flatMap(orgId -> organizationService.scopedManagerId(auth, null)
                        .flatMap(mid -> organizationService.people(orgId, mid))
                        .switchIfEmpty(organizationService.people(orgId, null)));
    }

    @GetMapping("/trips")
    @Operation(summary = "Trajets",
            description = "Admin : tous les trajets de l'organisation. Manager : trajets des flottes qui lui sont affectées.")
    public Flux<OrganizationDtos.OrganizationTrip> trips(
            @RequestParam(required = false) UUID adminId,
            @RequestParam(required = false) UUID managerId,
            Authentication auth
    ) {
        return organizationService.resolveOrganizationAdminId(auth, adminId)
                .flatMapMany(orgId -> organizationService.scopedManagerId(auth, managerId)
                        .flatMapMany(mid -> organizationService.trips(orgId, mid))
                        .switchIfEmpty(organizationService.trips(orgId, null)));
    }

    @GetMapping("/fuel-recharges")
    @Operation(summary = "Recharges carburant de l'organisation")
    public Flux<OrganizationDtos.OrganizationFuelRecharge> fuelRecharges(
            @RequestParam(required = false) UUID adminId,
            Authentication auth
    ) {
        return organizationService.resolveOrganizationAdminId(auth, adminId)
                .flatMapMany(orgId -> organizationService.scopedManagerId(auth, null)
                        .flatMapMany(mid -> organizationService.fuelRecharges(orgId, mid))
                        .switchIfEmpty(organizationService.fuelRecharges(orgId, null)));
    }

    @GetMapping("/maintenances")
    @Operation(summary = "Maintenances de l'organisation")
    public Flux<OrganizationDtos.OrganizationMaintenance> maintenances(
            @RequestParam(required = false) UUID adminId,
            Authentication auth
    ) {
        return organizationService.resolveOrganizationAdminId(auth, adminId)
                .flatMapMany(orgId -> organizationService.scopedManagerId(auth, null)
                        .flatMapMany(mid -> organizationService.maintenances(orgId, mid))
                        .switchIfEmpty(organizationService.maintenances(orgId, null)));
    }

    @GetMapping("/incidents")
    @Operation(summary = "Incidents de l'organisation (véhicules des managers de l'admin)")
    public Flux<OrganizationDtos.OrganizationIncident> incidents(
            @RequestParam(required = false) UUID adminId,
            Authentication auth
    ) {
        return organizationService.resolveOrganizationAdminId(auth, adminId)
                .flatMapMany(orgId -> organizationService.scopedManagerId(auth, null)
                        .flatMapMany(mid -> organizationService.incidents(orgId, mid))
                        .switchIfEmpty(organizationService.incidents(orgId, null)));
    }

    @GetMapping("/zones")
    @Operation(summary = "Zones de l'organisation",
            description = "Option managerId : zones définies par ce manager s'il appartient à l'org.")
    public Flux<OrganizationDtos.OrganizationZone> zones(
            @RequestParam(required = false) UUID adminId,
            @RequestParam(required = false) UUID managerId,
            Authentication auth
    ) {
        return organizationService.resolveOrganizationAdminId(auth, adminId)
                .flatMapMany(orgId -> organizationService.scopedManagerId(auth, managerId)
                        .flatMapMany(mid -> organizationService.zones(orgId, mid))
                        .switchIfEmpty(organizationService.zones(orgId, null)));
    }

    @GetMapping("/history")
    @Operation(summary = "Historique (trajets terminés/annulés + incidents)",
            description = "Admin : toute l'organisation. Manager : uniquement ses trajets et incidents.")
    public Flux<OrganizationDtos.OrganizationHistoryItem> history(
            @RequestParam(required = false) UUID adminId,
            Authentication auth
    ) {
        return organizationService.resolveOrganizationAdminId(auth, adminId)
                .flatMapMany(orgId -> organizationService.scopedManagerId(auth, null)
                        .flatMapMany(mid -> organizationService.history(orgId, mid))
                        .switchIfEmpty(organizationService.history(orgId, null)));
    }
}
