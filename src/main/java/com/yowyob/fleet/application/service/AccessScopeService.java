package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.FleetException;
import com.yowyob.fleet.domain.exception.TripException;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.PlatformAdminR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Périmètre d'action : ROLE + flottes affectées.
 * Admin d'organisation = toutes les flottes de son org.
 * Manager = uniquement les flottes dont il est manager_id.
 */
@Service
@RequiredArgsConstructor
public class AccessScopeService {

    private final UserLocalR2dbcRepository userRepo;
    private final PlatformAdminR2dbcRepository platformAdminRepo;
    private final FleetManagerR2dbcRepository managerRepo;
    private final FleetR2dbcRepository fleetRepo;
    private final VehicleLocalR2dbcRepository vehicleRepo;

    public Mono<UUID> resolveLocalUserId(UUID id) {
        if (id == null) {
            return Mono.empty();
        }
        return userRepo.findById(id)
                .switchIfEmpty(Mono.defer(() -> userRepo.findByKernelId(id)))
                .map(UserLocalEntity::getId)
                .defaultIfEmpty(id);
    }

    /**
     * Autorise si le caller gère la flotte, ou s'il est l'admin de l'organisation
     * à laquelle appartient cette flotte.
     */
    public Mono<Void> assertFleetAccess(UUID actorId, UUID fleetId) {
        if (fleetId == null) {
            return Mono.error(FleetException.accessDenied());
        }
        return resolveLocalUserId(actorId).flatMap(localId ->
                fleetRepo.existsByIdAndManagerId(fleetId, localId)
                        .flatMap(owns -> Boolean.TRUE.equals(owns)
                                ? Mono.empty()
                                : assertOrgAdminOfFleet(localId, fleetId)));
    }

    public Mono<Void> assertTripFleetAccess(UUID actorId, UUID fleetId) {
        return assertFleetAccess(actorId, fleetId)
                .onErrorMap(FleetException.class, e -> TripException.forbidden());
    }

    public Mono<Void> assertVehicleFleetAccess(UUID actorId, UUID fleetId) {
        if (fleetId == null) {
            return resolveLocalUserId(actorId).flatMap(localId ->
                    platformAdminRepo.existsById(localId)
                            .flatMap(isAdmin -> Boolean.TRUE.equals(isAdmin)
                                    ? Mono.empty()
                                    : Mono.error(new AccessDeniedException("Véhicule hors périmètre."))));
        }
        return assertFleetAccess(actorId, fleetId)
                .onErrorMap(e -> new AccessDeniedException("Véhicule hors périmètre."));
    }

    public Mono<Void> assertVehicleAccess(UUID actorId, UUID vehicleId) {
        return vehicleRepo.findById(vehicleId)
                .filter(v -> v.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new AccessDeniedException("Véhicule hors périmètre.")))
                .flatMap(v -> assertVehicleFleetAccess(actorId, v.getFleetId()));
    }

    private Mono<Void> assertOrgAdminOfFleet(UUID localId, UUID fleetId) {
        return fleetRepo.findById(fleetId)
                .filter(f -> f.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(FleetException.notFound(fleetId)))
                .flatMap(fleet -> platformAdminRepo.existsById(localId)
                        .flatMap(isAdmin -> {
                            if (!Boolean.TRUE.equals(isAdmin)) {
                                return Mono.error(FleetException.accessDenied());
                            }
                            if (localId.equals(fleet.getCompanyId()) || localId.equals(fleet.getCreatedBy())) {
                                return Mono.empty();
                            }
                            UUID managerId = fleet.getManagerId();
                            if (managerId == null) {
                                return Mono.error(FleetException.accessDenied());
                            }
                            return managerRepo.findById(managerId)
                                    .filter(m -> localId.equals(m.getCreatedByAdminId()))
                                    .switchIfEmpty(Mono.error(FleetException.accessDenied()))
                                    .then();
                        }));
    }

    public Mono<Boolean> isOrganizationAdmin(UUID actorId) {
        return resolveLocalUserId(actorId)
                .flatMap(platformAdminRepo::existsById)
                .defaultIfEmpty(false);
    }

    /**
     * Zone : propriétaire, flotte affectée dans le périmètre, ou admin de l'org du manager.
     */
    public Mono<Void> assertZoneAccess(UUID actorId, UUID fleetId, UUID zoneManagerId) {
        return resolveLocalUserId(actorId).flatMap(localId -> {
            if (zoneManagerId != null && localId.equals(zoneManagerId)) {
                return Mono.empty();
            }
            Mono<Void> viaFleet = fleetId != null
                    ? assertFleetAccess(localId, fleetId)
                    : Mono.error(FleetException.accessDenied());
            Mono<Void> viaOrg = zoneManagerId == null
                    ? Mono.error(FleetException.accessDenied())
                    : platformAdminRepo.existsById(localId).flatMap(isAdmin -> {
                        if (!Boolean.TRUE.equals(isAdmin)) {
                            return Mono.error(FleetException.accessDenied());
                        }
                        return managerRepo.findById(zoneManagerId)
                                .filter(m -> localId.equals(m.getCreatedByAdminId()))
                                .switchIfEmpty(Mono.error(FleetException.accessDenied()))
                                .then();
                    });
            return viaFleet.onErrorResume(e -> viaOrg);
        });
    }
}
