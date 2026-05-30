// Fichier : src/main/java/com/yowyob/fleet/application/service/SuperAdminService.java
package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.SuperAdminException;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.in.ManageSuperAdminUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity;
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
public class SuperAdminService implements ManageSuperAdminUseCase {

    private final AuthPort authPort;
    private final UserLocalR2dbcRepository userRepo;

    @Override
    public Mono<AuthPort.AuthResponse> createAdmin(AuthUseCase.RegisterCommand command) {
        return authPort.registerInRemote(command)
                .flatMap(res -> {
                    String token = res.accessToken();
                    UUID userId = res.user().id();

                    // Logic Photo comme dans AuthService
                    Mono<Void> photoFlow = (command.photo() != null) ?
                            authPort.updateProfilePicture(userId, token, command.photo())
                                    .onErrorResume(e -> {
                                        log.warn("📸 Photo Admin ignorée suite à erreur distante.");
                                        return Mono.empty();
                                    }) : Mono.empty();

                    return photoFlow
                            .then(authPort.getUserById(userId, token))
                            .flatMap(freshUser -> syncIdentityOnly(freshUser)
                                    .thenReturn(new AuthPort.AuthResponse(token, res.refreshToken(), freshUser)));
                });
    }

    @Override
    public Flux<AuthPort.UserDetail> listAdmins(String token) {
        return authPort.getUsersByService("FLEET_MANAGEMENT", token)
                .filter(u -> u.roles().contains("FLEET_ADMIN"))
                .flatMap(this::syncIdentityOnly);
    }

    @Override
    public Mono<AuthPort.UserDetail> getAdminDetails(UUID adminId, String token) {
        return authPort.getUserById(adminId, token)
                .filter(u -> u.roles().contains("FLEET_ADMIN"))
                .switchIfEmpty(Mono.error(SuperAdminException.roleMismatch()))
                .flatMap(this::syncIdentityOnly);
    }

    @Override
    public Mono<Void> toggleAdminStatus(UUID adminId, UUID requesterId) {
        if (adminId.equals(requesterId)) return Mono.error(SuperAdminException.selfActionForbidden());
        
        return userRepo.findById(adminId)
                .switchIfEmpty(Mono.error(SuperAdminException.adminNotFound()))
                .flatMap(u -> {
                    u.setActive(!u.isActive());
                    u.setNewRecord(false);
                    return userRepo.save(u);
                }).then();
    }

    /**
     * Synchronise les informations d'identité dans fleet.users
     */
   private Mono<AuthPort.UserDetail> syncIdentityOnly(AuthPort.UserDetail remote) {
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
                .map(local -> new AuthPort.UserDetail(
                        remote.id(), remote.username(), remote.email(), remote.phone(),
                        remote.firstName(), remote.lastName(), remote.service(),
                        remote.roles(), remote.permissions(), local.getPhotoUrl(), 
                        null, null, null, 
                        local.isActive(), 
                        local.getLastLoginAt()
                ));
    }
}