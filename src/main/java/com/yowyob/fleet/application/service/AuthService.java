package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.AuthException;
import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.in.ManageDriverUseCase;
import com.yowyob.fleet.domain.ports.out.*;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements AuthUseCase {

    private final AuthPort authPort;
    private final UserLocalR2dbcRepository userRepo;
    private final DriverPersistencePort driverPort;
    private final FleetManagerPersistencePort managerPort;
    private final FleetManagerR2dbcRepository managerRepo;
    private final FleetR2dbcRepository fleetRepo;
    private final ManageDriverUseCase driverUseCase;

    @Override
    public Mono<AuthPort.AuthResponse> login(
        String identifier,
        String password
    ) {
        return authPort
            .login(identifier, password)
            .onErrorResume(e -> {
                if (
                    e instanceof AuthException ae &&
                    ae.getStatus() == HttpStatus.UNAUTHORIZED
                ) {
                    return Mono.error(AuthException.invalidCredentials());
                }
                return Mono.error(e);
            })
            .flatMap(response ->
                resolveLocalUser(response.user())
                    .flatMap(localId ->
                        ensureRoleProfileExistsForLocalId(response.user(), localId)
                            .then(checkUserAccessByLocalId(localId))
                    )
                    .thenReturn(response)
            );
    }

    @Override
    public Mono<AuthPort.AuthResponse> register(RegisterCommand command) {
        return ensureRolesExist(command.roles())
            .then(authPort.registerInRemote(command))
            // --- AJOUT ICI ---
            .onErrorResume(e -> {
                if (
                    e instanceof AuthException ae &&
                    ae.getBusinessCode().equals("AUTH_004")
                ) {
                    // 409 Conflict
                    return userRepo
                        .findByUsername(command.username()) // On cherche en local
                        .flatMap(u -> {
                            if (!u.isActive()) {
                                return Mono.<AuthPort.AuthResponse>error(
                                    new AuthException(
                                        "Ce compte est désactivé. Veuillez contacter un administrateur pour le réactiver.",
                                        HttpStatus.FORBIDDEN,
                                        "AUTH_007"
                                    )
                                );
                            }
                            return Mono.<AuthPort.AuthResponse>error(e);
                        })
                        .switchIfEmpty(Mono.error(e));
                }
                return Mono.error(e);
            })
            // --- FIN AJOUT ---
            .flatMap(res -> {
                String token = res.accessToken();
                UUID userId = res.user().id();

                // 1. TENTATIVE PHOTO (NON-BLOQUANTE)
                Mono<Void> photoFlow = (command.photo() != null)
                    ? this.updateProfilePicture(
                          userId,
                          token,
                          command.photo()
                      ).onErrorResume(e -> {
                          log.warn(
                              "📸 Photo ignorée au register suite à erreur distante."
                          );
                          return Mono.empty();
                      })
                    : Mono.empty();

                // 2. CHAINAGE : Photo -> Fetch Final -> Sync Local
                return photoFlow
                    .then(authPort.getUserById(userId, token))
                    .flatMap(freshUser ->
                        resolveLocalUser(freshUser)
                            .flatMap(localId -> ensureRoleProfileExistsForLocalId(freshUser, localId))
                            .thenReturn(
                                new AuthPort.AuthResponse(
                                    token,
                                    res.refreshToken(),
                                    freshUser
                                )
                            )
                    );
            });
    }

    @Override
    public Mono<AuthPort.UserDetail> me(String token) {
        return authPort
            .getUserProfile(token)
            .flatMap(summary -> authPort.getUserById(summary.id(), token))
            .flatMap(remote ->
                resolveLocalUser(remote)
                    .flatMap(localId -> ensureRoleProfileExistsForLocalId(remote, localId))
                    .thenReturn(remote)
            )
            .flatMap(this::enrichWithLocalData);
    }

    /**
     * FONCTION CENTRALE : Garantit que Hassana voit ses managers/drivers en DB locale.
     * Accepte optionnellement l'UUID local (peut différer de user.id() si merge seed)
     */
    private Mono<Void> ensureRoleProfileExists(AuthPort.UserDetail user) {
        return ensureRoleProfileExistsForLocalId(user, user.id());
    }

    private Mono<Void> ensureRoleProfileExistsForLocalId(AuthPort.UserDetail user, UUID localUserId) {
        if (user.roles().contains("FLEET_MANAGER")) {
            return managerRepo.existsById(localUserId).flatMap(exists -> {
                if (exists) return Mono.empty();
                // Créer le profil manager avec l'UUID local (pas l'UUID Kernel)
                return managerPort
                    .createProfile(localUserId, "Société de " + user.lastName())
                    .then(
                        userRepo
                            .findById(localUserId)
                            .switchIfEmpty(Mono.defer(() -> userRepo.findByKernelId(user.id())))
                            .flatMap(localUser -> {
                                localUser.setActive(false);
                                localUser.setApprovalStatus("PENDING");
                                localUser.setNew(false);
                                return userRepo.save(localUser);
                            })
                            .then()
                    );
            });
        }
        if (user.roles().contains("FLEET_DRIVER")) {
            return driverPort
                .findById(localUserId)
                .flatMap(exists -> Mono.<Void>empty())
                .switchIfEmpty(
                    Mono.defer(() -> {
                        Driver d = new Driver(
                            localUserId,
                            null,
                            "PENDING-" + localUserId.toString().substring(0, 8),
                            "ACTIVE",
                            null,
                            ""
                        );
                        return driverPort.save(d).then();
                    })
                );
        }
        return Mono.empty();
    }

    // --- AUTRES MÉTHODES (SYCHRONISÉES) ---

    @Override
    public Mono<AuthPort.AuthResponse> refreshToken(String refreshToken) {
        return authPort
            .refresh(refreshToken)
            .flatMap(response ->
                resolveLocalUser(response.user())
                    .flatMap(localId -> ensureRoleProfileExistsForLocalId(response.user(), localId))
                    .thenReturn(response)
            );
    }

    @Override
    public Mono<AuthPort.UserDetail> updateProfile(
        UUID userId,
        String token,
        UpdateProfileCommand command
    ) {
        return authPort
            .updateUserProfile(userId, token, command)
            .flatMap(remote -> resolveLocalUser(remote).thenReturn(remote))
            .flatMap(this::enrichWithLocalData);
    }

    @Override
    public Mono<Void> updateProfilePicture(
        UUID userId,
        String token,
        FileContent file
    ) {
        return authPort
            .updateProfilePicture(userId, token, file)
            .delayElement(Duration.ofMillis(600))
            .then(authPort.getUserById(userId, token))
            .flatMap(remote -> resolveLocalUser(remote).then());
    }

    @Override
    @Transactional
    public Mono<Void> deleteAccount(UUID userId, String token) {
        return authPort
            .getUserProfile(token)
            .flatMap(user -> {
                if (user.roles().contains("FLEET_MANAGER")) {
                    // Résoudre l'UUID local
                    return userRepo.findByKernelId(userId)
                        .switchIfEmpty(Mono.defer(() -> userRepo.findById(userId)))
                        .flatMap(localUser ->
                            fleetRepo.findAllByManagerId(localUser.getId())
                                .hasElements()
                                .flatMap(has ->
                                    has
                                        ? Mono.<Void>error(new AuthException(
                                                "Supprimez vos flottes d'abord.",
                                                HttpStatus.CONFLICT,
                                                "ACC_001"))
                                        : Mono.empty()
                                )
                        );
                }
                if (user.roles().contains("FLEET_DRIVER")) {
                    return driverUseCase.unassignVehicle(userId, userId);
                }
                return Mono.<Void>empty();
            })
            .then(
                userRepo.findByKernelId(userId)
                    .switchIfEmpty(Mono.defer(() -> userRepo.findById(userId)))
                    .flatMap(local -> {
                        local.setActive(false);
                        local.setDeletedAt(Instant.now());
                        local.setNew(false);
                        return userRepo.save(local);
                    })
            )
            .then(authPort.moveUserToService(userId, "DELETED_USER", token));
    }

    /**
     * Sync le user en DB locale et retourne son UUID LOCAL (peut ≠ UUID Kernel si merge seed).
     */
    private Mono<UUID> resolveLocalUser(AuthPort.UserDetail remote) {
        return userRepo.findByKernelId(remote.id())
            .switchIfEmpty(Mono.defer(() -> userRepo.findById(remote.id())))
            .flatMap(local -> {
                local.setUsername(remote.username());
                local.setEmail(remote.email());
                local.setFirstName(remote.firstName());
                local.setLastName(remote.lastName());
                local.setPhotoUrl(remote.photoUrl());
                local.setLastLoginAt(Instant.now());
                local.setKernelId(remote.id());
                local.setNew(false);
                return userRepo.save(local).map(saved -> saved.getId());
            })
            .switchIfEmpty(Mono.defer(() ->
                userRepo.findByEmail(remote.email())
                    .flatMap(existingByEmail -> {
                        log.info("🔄 [SYNC] Merge email={} — local id={}, kernel_id={}",
                                remote.email(), existingByEmail.getId(), remote.id());
                        existingByEmail.setUsername(
                            remote.username() != null ? remote.username() : existingByEmail.getUsername());
                        existingByEmail.setFirstName(
                            remote.firstName() != null ? remote.firstName() : existingByEmail.getFirstName());
                        existingByEmail.setLastName(
                            remote.lastName() != null ? remote.lastName() : existingByEmail.getLastName());
                        existingByEmail.setPhotoUrl(
                            remote.photoUrl() != null ? remote.photoUrl() : existingByEmail.getPhotoUrl());
                        existingByEmail.setLastLoginAt(Instant.now());
                        existingByEmail.setKernelId(remote.id());
                        existingByEmail.setNew(false);
                        return userRepo.save(existingByEmail).map(saved -> saved.getId());
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        log.info("📝 [SYNC] Nouveau user : kernel_id={} email={}", remote.id(), remote.email());
                        UserLocalEntity n = UserLocalEntity.builder()
                            .id(remote.id())
                            .username(remote.username())
                            .email(remote.email())
                            .firstName(remote.firstName())
                            .lastName(remote.lastName())
                            .photoUrl(remote.photoUrl())
                            .isActive(true)
                            .lastLoginAt(Instant.now())
                            .kernelId(remote.id())
                            .build();
                        n.setNew(true);
                        return userRepo.save(n).map(saved -> saved.getId());
                    }))
            ));
    }

    private Mono<Void> checkUserAccessByLocalId(UUID localUserId) {
        return userRepo.findById(localUserId).flatMap(u -> {
            if (u.getDeletedAt() != null) return Mono.error(AuthException.accountDeleted());
            if (!u.isActive()) return Mono.error(AuthException.accountLocked());
            return Mono.empty();
        });
    }

    @Override
    public Mono<Void> changePassword(UUID u, String t, String c, String n) {
        return authPort.changePassword(u, t, c, n);
    }

    private Mono<Void> ensureRolesExist(List<String> roles) {
        return Flux.fromIterable(roles)
            .flatMap(r ->
                authPort
                    .roleExists(r)
                    .flatMap(ex -> !ex ? authPort.createRole(r) : Mono.empty())
            )
            .then();
    }

    private Mono<Void> createLocalProfile(AuthPort.UserDetail user) {
        return ensureRoleProfileExists(user); // Réutilisation pour éviter les doublons
    }

    private Mono<AuthPort.UserDetail> enrichWithLocalData(
        AuthPort.UserDetail remote
    ) {
        if (remote.roles().contains("FLEET_MANAGER")) {
            return managerPort
                .getCompanyName(remote.id())
                .map(c ->
                    new AuthPort.UserDetail(
                        remote.id(),
                        remote.username(),
                        remote.email(),
                        remote.phone(),
                        remote.firstName(),
                        remote.lastName(),
                        remote.service(),
                        remote.roles(),
                        remote.permissions(),
                        remote.photoUrl(),
                        c,
                        null,
                        null,
                        remote.isActive(),
                        remote.lastLoginAt()
                    )
                )
                .defaultIfEmpty(remote);
        }
        if (remote.roles().contains("FLEET_DRIVER")) {
            return driverPort
                .findById(remote.id())
                .map(d ->
                    new AuthPort.UserDetail(
                        remote.id(),
                        remote.username(),
                        remote.email(),
                        remote.phone(),
                        remote.firstName(),
                        remote.lastName(),
                        remote.service(),
                        remote.roles(),
                        remote.permissions(),
                        remote.photoUrl(),
                        null,
                        d.licenceNumber(),
                        d.assignedVehicleId() != null
                            ? d.assignedVehicleId().toString()
                            : null,
                        remote.isActive(),
                        remote.lastLoginAt()
                    )
                )
                .defaultIfEmpty(remote);
        }
        return Mono.just(remote);
    }
}
