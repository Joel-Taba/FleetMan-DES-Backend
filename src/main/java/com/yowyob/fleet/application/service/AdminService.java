package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.AdminException;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.in.ManageAdminUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.domain.ports.out.ExternalActorPort;
import com.yowyob.fleet.domain.ports.out.FleetManagerPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetManagerEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService implements ManageAdminUseCase {

    private final UserLocalR2dbcRepository userRepo;
    private final FleetManagerR2dbcRepository managerRepo;
    private final AuthPort authPort;
    private final ExternalActorPort externalActorPort;
    private final FleetManagerPersistencePort managerPersistencePort;
    private final FleetR2dbcRepository fleetRepo;

    @Override
    public Flux<AuthPort.UserDetail> listFleetManagers(String token) {
        return managerRepo.findAll()
                .flatMap(mgr -> userRepo.findById(mgr.getUserId())
                        .filter(user -> user.getDeletedAt() == null)
                        .map(user -> toManagerDetail(user, mgr)))
                .sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                        safeName(a), safeName(b)));
    }

    @Override
    public Mono<AuthPort.UserDetail> getManagerDetails(UUID managerId, String token, boolean isSuperAdmin) {
        return userRepo.findById(managerId)
                .switchIfEmpty(Mono.defer(() -> userRepo.findByKernelId(managerId)))
                .filter(user -> user.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(AdminException.managerNotFound()))
                .flatMap(user -> managerRepo.findById(user.getId())
                        .switchIfEmpty(Mono.error(AdminException.managerNotFound()))
                        .map(mgr -> toManagerDetail(user, mgr)));
    }

    @Override
    public Mono<Void> deleteManager(UUID managerId) {
        return userRepo.findById(managerId)
                .switchIfEmpty(Mono.defer(() -> userRepo.findByKernelId(managerId)))
                .filter(user -> user.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(AdminException.managerNotFound()))
                .flatMap(user -> fleetRepo.countByManagerId(user.getId())
                        .flatMap(count -> {
                            if (count > 0) {
                                return Mono.error(AdminException.managerHasActiveFleets());
                            }
                            user.setDeletedAt(Instant.now());
                            user.setActive(false);
                            user.setNewRecord(false);
                            return userRepo.save(user);
                        }))
                .doOnSuccess(u -> log.info("🗑️ Manager {} supprimé (soft delete)", managerId))
                .then();
    }

    @Override
    public Mono<Void> toggleManagerStatus(UUID managerId, UUID requesterId, boolean isSuperAdmin) {
        return userRepo.findById(managerId)
                .switchIfEmpty(Mono.defer(() -> userRepo.findByKernelId(managerId)))
                .switchIfEmpty(Mono.error(AdminException.managerNotFound()))
                .flatMap(u -> {
                    u.setActive(!u.isActive());
                    u.setNewRecord(false);
                    return userRepo.save(u);
                })
                .doOnSuccess(u -> log.info("🔐 Statut Manager {} changé vers : {}", managerId, u.isActive()))
                .then();
    }

    @Override
    public Mono<AuthPort.UserDetail> createManager(AuthUseCase.RegisterCommand command, String companyName) {
        return authPort.registerInRemote(command)
                .flatMap(res -> {
                    // registerInRemote ne renvoie pas de session exploitable (voir
                    // SuperAdminService.createAdmin) : on utilise directement le detail
                    // renvoyé (id/username/email/phone Kernel + firstName/lastName/roles
                    // de la commande) plutôt que de re-fetcher avec un token vide.
                    UUID userId = res.user().id();

                    Mono<Void> roleFlow = externalActorPort.assignPlatformRole(userId, "FLEET_MANAGER")
                            .onErrorResume(e -> {
                                log.warn("⚠️ Assignation rôle FLEET_MANAGER ignorée pour {} : {}", userId, e.getMessage());
                                return Mono.empty();
                            });

                    // syncNewManagerIdentity DOIT s'exécuter avant createProfile : la table
                    // fleet.fleet_managers a une FK vers fleet.users(id), qui n'existe pas
                    // encore tant que le mirroir local n'a pas été créé.
                    return roleFlow
                            .then(syncNewManagerIdentity(res.user()))
                            .flatMap(detail -> managerPersistencePort.createProfile(userId, companyName)
                                    .thenReturn(detail));
                });
    }

    private Mono<AuthPort.UserDetail> syncNewManagerIdentity(AuthPort.UserDetail remote) {
        return userRepo.findById(remote.id())
                .switchIfEmpty(Mono.defer(() -> {
                    UserLocalEntity n = UserLocalEntity.builder()
                            .id(remote.id()).username(remote.username()).email(remote.email())
                            .phone(remote.phone())
                            .firstName(remote.firstName()).lastName(remote.lastName())
                            .isActive(true).build();
                    n.setNewRecord(true);
                    return userRepo.save(n);
                }))
                .thenReturn(remote);
    }

    private static String safeName(AuthPort.UserDetail u) {
        String name = ((u.firstName() != null ? u.firstName() : "")
                + " " + (u.lastName() != null ? u.lastName() : "")).trim();
        return name.isBlank() ? (u.username() != null ? u.username() : "") : name;
    }

    private AuthPort.UserDetail toManagerDetail(UserLocalEntity user, FleetManagerEntity mgr) {
        return new AuthPort.UserDetail(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getFirstName(),
                user.getLastName(),
                "FLEET_MANAGEMENT",
                List.of("FLEET_MANAGER"),
                List.of(),
                user.getPhotoUrl(),
                mgr.getCompanyName(),
                null,
                null,
                user.isActive(),
                user.getLastLoginAt()
        );
    }
}
