package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.AdminException;
import com.yowyob.fleet.domain.ports.in.ManageAdminUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.domain.ports.out.FleetManagerPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService implements ManageAdminUseCase {

    private final AuthPort authPort;
    private final UserLocalR2dbcRepository userRepo;
    private final FleetManagerR2dbcRepository managerRepo;
    private final FleetManagerPersistencePort managerPersistencePort;

    @Override
    public Flux<AuthPort.UserDetail> listFleetManagers(String token) {
        return authPort.getUsersByService("FLEET_MANAGEMENT", token)
                .filter(u -> u.roles().contains("FLEET_MANAGER"))
                .flatMap(remote -> syncIdentityAndRepairProfile(remote));
    }

    @Override
    public Mono<AuthPort.UserDetail> getManagerDetails(UUID managerId, String token, boolean isSuperAdmin) {
        return authPort.getUserById(managerId, token)
                .flatMap(remote -> {
                    if (!isSuperAdmin && remote.roles().contains("FLEET_SUPER_ADMIN")) {
                        return Mono.error(AdminException.masterAccessForbidden());
                    }
                    if (!remote.roles().contains("FLEET_MANAGER")) {
                        return Mono.error(AdminException.actionForbiddenOnUserType());
                    }
                    return syncIdentityAndRepairProfile(remote);
                });
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

    /**
     * COEUR DE LA SYNCHRO :
     * 1. Met à jour l'identité (fleet.users) depuis le service Auth.
     * 2. Vérifie/Crée le profil métier (fleet.fleet_managers) - SELF HEALING.
     * 3. Retourne le UserDetail enrichi.
     */
    private Mono<AuthPort.UserDetail> syncIdentityAndRepairProfile(AuthPort.UserDetail remote) {
        return userRepo.findById(remote.id())
                .flatMap(local -> {
                    local.setUsername(remote.username());
                    local.setEmail(remote.email());
                    local.setFirstName(remote.firstName());
                    local.setLastName(remote.lastName());
                    local.setPhotoUrl(remote.photoUrl());
                    local.setNewRecord(false);
                    return userRepo.save(local);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    UserLocalEntity n = UserLocalEntity.builder()
                            .id(remote.id()).username(remote.username()).email(remote.email())
                            .firstName(remote.firstName()).lastName(remote.lastName())
                            .photoUrl(remote.photoUrl()).isActive(true).build();
                    n.setNewRecord(true);
                    return userRepo.save(n);
                }))
                .flatMap(localUser ->
                    managerRepo.existsById(localUser.getId())
                        .flatMap(exists -> {
                            if (Boolean.TRUE.equals(exists)) {
                                return Mono.empty();
                            }
                            return managerPersistencePort
                                    .createProfile(localUser.getId(), "Société de " + remote.lastName())
                                    .onErrorResume(e -> {
                                        log.warn("⚠️ Profil manager non créé pour {} : {}",
                                                localUser.getId(), e.getMessage());
                                        return Mono.empty();
                                    });
                        })
                        .then(managerPersistencePort.getCompanyName(localUser.getId())
                                .defaultIfEmpty(""))
                        .map(company -> new AuthPort.UserDetail(
                                localUser.getId(),
                                remote.username(),
                                remote.email(),
                                remote.phone(),
                                remote.firstName(),
                                remote.lastName(),
                                remote.service(),
                                remote.roles() != null ? remote.roles() : java.util.List.of("FLEET_MANAGER"),
                                remote.permissions(),
                                remote.photoUrl(),
                                company.isBlank() ? null : company,
                                null,
                                null,
                                localUser.isActive(),
                                localUser.getLastLoginAt()
                        ))
                );
    }
}