package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.SuperAdminException;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.in.ManageSuperAdminUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.domain.ports.out.ExternalActorPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.PlatformAdminEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.PlatformAdminR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminService implements ManageSuperAdminUseCase {

    private final AuthPort authPort;
    private final UserLocalR2dbcRepository userRepo;
    private final PlatformAdminR2dbcRepository platformAdminRepo;
    private final ExternalActorPort externalActorPort;

    @Override
    public Mono<AuthPort.UserDetail> createAdmin(AuthUseCase.RegisterCommand command) {
        return authPort.registerInRemote(command)
                .flatMap(res -> {
                    // registerInRemote ne renvoie pas de session (accessToken/refreshToken vides
                    // par construction) : impossible d'appeler getUserById avec ce token (il n'y
                    // en a pas). Le detail créé par registerInRemote contient déjà id/username/
                    // email/phone (issus du Kernel) + firstName/lastName/roles (issus de la
                    // commande) : on l'utilise directement au lieu de le "re-fetcher".
                    UUID userId = res.user().id();

                    Mono<Void> photoFlow = (command.photo() != null)
                            ? authPort.updateProfilePicture(userId, "", command.photo())
                                    .onErrorResume(e -> {
                                        log.warn("📸 Photo Admin ignorée suite à erreur distante.");
                                        return Mono.empty();
                                    })
                            : Mono.empty();

                    // Assignation du rôle FLEET_ADMIN côté Kernel (portée TENANT) : sans cette
                    // étape, l'utilisateur créé n'a aucun rôle Kernel et ne peut pas se
                    // connecter avec les permissions Admin (JWT sans ROLE_FLEET_ADMIN).
                    Mono<Void> roleFlow = externalActorPort.assignPlatformRole(userId, "FLEET_ADMIN")
                            .onErrorResume(e -> {
                                log.warn("⚠️ Assignation rôle FLEET_ADMIN ignorée pour {} : {}", userId, e.getMessage());
                                return Mono.empty();
                            });

                    return photoFlow
                            .then(roleFlow)
                            .then(syncIdentityOnly(res.user()))
                            .flatMap(detail -> platformAdminRepo.save(new PlatformAdminEntity(userId))
                                    .thenReturn(detail));
                });
    }

    // Le Kernel expose GET /api/users/admins pour lister les FLEET_ADMIN, mais
    // renvoie un tableau JSON brut (non enveloppé ApiResponse) que le client
    // FleetMan ne peut pas désérialiser (échec silencieux -> Flux.empty() à
    // chaque appel). fleet.platform_admins est le marqueur local fiable
    // équivalent à fleet.fleet_managers pour les gestionnaires : c'est lui qui
    // fait foi, pas le Kernel, pour savoir qui est administrateur.
    @Override
    public Flux<AuthPort.UserDetail> listAdmins(String token) {
        return platformAdminRepo.findAll()
                .flatMap(pa -> userRepo.findById(pa.getUserId()))
                .map(this::localUserToAdminDetail)
                .sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                        a.username() != null ? a.username() : "",
                        b.username() != null ? b.username() : ""
                ));
    }

    @Override
    public Mono<AuthPort.UserDetail> getAdminDetails(UUID adminId, String token) {
        return platformAdminRepo.findById(adminId)
                .switchIfEmpty(Mono.error(SuperAdminException.roleMismatch()))
                .flatMap(pa -> userRepo.findById(adminId))
                .switchIfEmpty(Mono.error(SuperAdminException.adminNotFound()))
                .map(this::localUserToAdminDetail);
    }

    @Override
    public Mono<AuthPort.UserDetail> toggleAdminStatus(UUID adminId, UUID requesterId) {
        if (adminId.equals(requesterId)) return Mono.error(SuperAdminException.selfActionForbidden());

        return userRepo.findById(adminId)
                .switchIfEmpty(Mono.error(SuperAdminException.adminNotFound()))
                .flatMap(u -> {
                    u.setActive(!u.isActive());
                    u.setNewRecord(false);
                    return userRepo.save(u);
                })
                .map(this::localUserToAdminDetail);
    }

    private AuthPort.UserDetail localUserToAdminDetail(UserLocalEntity local) {
        return new AuthPort.UserDetail(
                local.getId(),
                local.getUsername(),
                local.getEmail(),
                local.getPhone(),
                local.getFirstName(),
                local.getLastName(),
                "FLEET_MANAGEMENT",
                List.of("FLEET_ADMIN"),
                List.of(),
                local.getPhotoUrl(),
                null,
                null,
                null,
                local.isActive(),
                local.getLastLoginAt()
        );
    }

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
