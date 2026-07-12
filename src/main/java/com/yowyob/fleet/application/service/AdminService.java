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

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService implements ManageAdminUseCase {

    private final AuthPort authPort;
    private final UserLocalR2dbcRepository userRepo;
    private final FleetManagerPersistencePort managerPersistencePort;

    @Override
    public Flux<AuthPort.UserDetail> listFleetManagers(String token) {
        return authPort.getUserProfile(token)
                .flatMapMany(currentUser -> {
                    boolean isSuper = currentUser.roles() != null && currentUser.roles().contains("FLEET_SUPER_ADMIN");
                    if (isSuper) {
                        return authPort.getUsersByService("FLEET_MANAGEMENT", token)
                                .flatMap(remote -> syncIdentityAndRepairProfile(remote));
                    }
                    return managerPersistencePort.getCompanyName(currentUser.id())
                            .flatMapMany(currentCompany -> {
                                if (currentCompany == null || currentCompany.isEmpty()) {
                                    return Flux.empty();
                                }
                                return authPort.getUsersByService("FLEET_MANAGEMENT", token)
                                        .flatMap(remote -> syncIdentityAndRepairProfile(remote))
                                        .filter(enriched -> enriched.companyName() != null &&
                                                currentCompany.equalsIgnoreCase(enriched.companyName()));
                            });
                });
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
        // On vérifie d'abord que c'est bien un manager
        return this.getManagerDetails(managerId, "", isSuperAdmin)
                .then(userRepo.findById(managerId))
                .switchIfEmpty(Mono.error(AdminException.managerNotFound()))
                .flatMap(u -> {
                    u.setActive(!u.isActive());
                    u.setNewRecord(false); // On force l'UPDATE
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
                .flatMap(localUser -> managerPersistencePort
                        .createProfile(remote.id(), "Société de " + remote.lastName())
                        .onErrorResume(e -> Mono.empty())
                        .then(managerPersistencePort.getCompanyName(remote.id()))
                        .map(company -> new AuthPort.UserDetail(
                                remote.id(), remote.username(), remote.email(), remote.phone(),
                                remote.firstName(), remote.lastName(), remote.service(),
                                remote.roles(), remote.permissions(), remote.photoUrl(),
                                company, null, null,
                                localUser.isActive(), // <--- À l'intérieur des parenthèses
                                localUser.getLastLoginAt() // <--- À l'intérieur des parenthèses
                        )));
    }
}