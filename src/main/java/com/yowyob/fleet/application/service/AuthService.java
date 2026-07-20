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
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${application.kernel.tenant-id:}")
    private String defaultTenantId;

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
                            .then(userRepo.findById(localId))
                    )
                    .map(local -> new AuthPort.AuthResponse(
                            response.accessToken(),
                            response.refreshToken(),
                            mergeLocalIdentity(response.user(), local)
                    ))
            );
    }

    @Override
    public Mono<AuthPort.AuthResponse> selectContext(
        String selectionToken,
        String contextId,
        UUID organizationId
    ) {
        return authPort
            .selectContext(selectionToken, contextId, organizationId)
            .flatMap(response ->
                resolveLocalUser(response.user())
                    .flatMap(localId ->
                        ensureRoleProfileExistsForLocalId(response.user(), localId)
                            .then(checkUserAccessByLocalId(localId))
                            .then(userRepo.findById(localId))
                    )
                    .map(local -> new AuthPort.AuthResponse(
                            response.accessToken(),
                            response.refreshToken(),
                            mergeLocalIdentity(response.user(), local)
                    ))
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
                boolean hasSessionToken = token != null && !token.isBlank();

                // 1. TENTATIVE PHOTO (NON-BLOQUANTE)
                Mono<Void> photoFlow = (command.photo() != null && hasSessionToken)
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
                Mono<AuthPort.UserDetail> userAfterRegistration = hasSessionToken
                    ? authPort.getUserById(userId, token).defaultIfEmpty(res.user())
                    : Mono.just(res.user());

                return photoFlow
                    .then(userAfterRegistration)
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
            .flatMap(summary -> authPort.getUserById(summary.id(), token).defaultIfEmpty(summary))
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
                            "",
                            null
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
            .switchIfEmpty(Mono.error(AuthException.tokenExpired()))
            .flatMap(response -> {
                Mono<AuthPort.UserDetail> remoteUser = response.user() != null && response.user().id() != null
                    ? Mono.just(response.user())
                    : authPort.getUserProfile(response.accessToken());
                return remoteUser.flatMap(user ->
                    resolveLocalUser(user)
                        .flatMap(localId ->
                            ensureRoleProfileExistsForLocalId(user, localId)
                                .then(userRepo.findById(localId))
                        )
                        .map(local -> new AuthPort.AuthResponse(
                                response.accessToken(),
                                response.refreshToken(),
                                mergeLocalIdentity(user, local)
                        ))
                );
            });
    }

    @Override
    public Mono<AuthPort.UserDetail> updateProfile(
        UUID userId,
        String token,
        UpdateProfileCommand command
    ) {
        return userRepo.findByKernelId(userId)
            .switchIfEmpty(Mono.defer(() -> userRepo.findById(userId)))
            .switchIfEmpty(Mono.error(new AuthException(
                    "Utilisateur local introuvable.", HttpStatus.NOT_FOUND, "AUTH_404")))
            .flatMap(local -> {
                if (command.firstName() != null && !command.firstName().isBlank()) {
                    local.setFirstName(command.firstName().trim());
                }
                if (command.lastName() != null && !command.lastName().isBlank()) {
                    local.setLastName(command.lastName().trim());
                }
                if (command.email() != null && !command.email().isBlank()) {
                    local.setEmail(command.email().trim());
                }
                if (command.phone() != null) {
                    local.setPhone(command.phone().isBlank() ? null : command.phone().trim());
                }
                local.setNew(false);
                return userRepo.save(local);
            })
            .flatMap(saved ->
                authPort.getUserProfile(token)
                    .map(remote -> mergeLocalIdentity(remote, saved))
                    .flatMap(this::enrichWithLocalData)
            );
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
                mergeRemoteIntoLocal(local, remote);
                local.setLastLoginAt(Instant.now());
                local.setKernelId(remote.id());
                if (local.getTenantId() == null) {
                    local.setTenantId(resolveTenantId());
                }
                local.setNew(false);
                return userRepo.save(local).map(UserLocalEntity::getId);
            })
            .switchIfEmpty(Mono.defer(() ->
                userRepo.findByEmail(remote.email())
                    .flatMap(existingByEmail -> {
                        log.info("🔄 [SYNC] Merge email={} — local id={}, kernel_id={}",
                                remote.email(), existingByEmail.getId(), remote.id());
                        mergeRemoteIntoLocal(existingByEmail, remote);
                        existingByEmail.setLastLoginAt(Instant.now());
                        existingByEmail.setKernelId(remote.id());
                        if (existingByEmail.getTenantId() == null) {
                            existingByEmail.setTenantId(resolveTenantId());
                        }
                        existingByEmail.setNew(false);
                        return userRepo.save(existingByEmail).map(UserLocalEntity::getId);
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        log.info("📝 [SYNC] Nouveau user : kernel_id={} email={}", remote.id(), remote.email());
                        UserLocalEntity n = UserLocalEntity.builder()
                            .id(remote.id())
                            .username(nonBlank(remote.username(), remote.email()))
                            .email(nonBlank(remote.email(), remote.username() + "@local.fleetman"))
                            .firstName(remote.firstName())
                            .lastName(remote.lastName())
                            .phone(remote.phone())
                            .photoUrl(remote.photoUrl())
                            .isActive(true)
                            .lastLoginAt(Instant.now())
                            .kernelId(remote.id())
                            .tenantId(resolveTenantId())
                            .build();
                        n.setNew(true);
                        return userRepo.save(n).map(UserLocalEntity::getId);
                    }))
            ));
    }

    private static void mergeRemoteIntoLocal(UserLocalEntity local, AuthPort.UserDetail remote) {
        if (nonBlank(remote.username(), null) != null && !looksLikeUuid(remote.username())) {
            local.setUsername(remote.username());
        }
        if (nonBlank(remote.email(), null) != null) {
            local.setEmail(remote.email());
        }
        if (nonBlank(remote.firstName(), null) != null) {
            local.setFirstName(remote.firstName());
        }
        if (nonBlank(remote.lastName(), null) != null) {
            local.setLastName(remote.lastName());
        }
        if (nonBlank(remote.phone(), null) != null) {
            local.setPhone(remote.phone());
        }
        if (nonBlank(remote.photoUrl(), null) != null) {
            local.setPhotoUrl(remote.photoUrl());
        }
    }

    private static boolean looksLikeUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value.trim());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String nonBlank(String value, String fallback) {
        if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value.trim())) {
            return value.trim();
        }
        return fallback;
    }

    private AuthPort.UserDetail mergeLocalIdentity(AuthPort.UserDetail remote, UserLocalEntity local) {
        String remoteUsername = looksLikeUuid(remote.username()) ? null : remote.username();
        return new AuthPort.UserDetail(
            local.getId(),
            nonBlank(remoteUsername, local.getUsername()),
            nonBlank(remote.email(), local.getEmail()),
            nonBlank(remote.phone(), local.getPhone()),
            nonBlank(remote.firstName(), local.getFirstName()),
            nonBlank(remote.lastName(), local.getLastName()),
            remote.service(),
            remote.roles(),
            remote.permissions(),
            nonBlank(remote.photoUrl(), local.getPhotoUrl()),
            remote.companyName(),
            remote.licenceNumber(),
            remote.vehicleId(),
            local.isActive(),
            remote.lastLoginAt() != null ? remote.lastLoginAt() : local.getLastLoginAt()
        );
    }

    private UUID resolveTenantId() {
        if (defaultTenantId == null || defaultTenantId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(defaultTenantId);
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Tenant ID invalide dans la configuration: {}", defaultTenantId);
            return null;
        }
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
        return userRepo.findByKernelId(remote.id())
            .switchIfEmpty(Mono.defer(() -> userRepo.findById(remote.id())))
            .map(local -> mergeLocalIdentity(remote, local))
            .defaultIfEmpty(remote)
            .flatMap(merged -> {
                if (merged.roles().contains("FLEET_MANAGER")) {
                    return managerPort
                        .getCompanyName(merged.id())
                        .map(c ->
                            new AuthPort.UserDetail(
                                merged.id(),
                                merged.username(),
                                merged.email(),
                                merged.phone(),
                                merged.firstName(),
                                merged.lastName(),
                                merged.service(),
                                merged.roles(),
                                merged.permissions(),
                                merged.photoUrl(),
                                c,
                                merged.licenceNumber(),
                                merged.vehicleId(),
                                merged.isActive(),
                                merged.lastLoginAt()
                            )
                        )
                        .defaultIfEmpty(merged);
                }
                if (merged.roles().contains("FLEET_DRIVER")) {
                    return driverPort
                        .findById(merged.id())
                        .map(d ->
                            new AuthPort.UserDetail(
                                merged.id(),
                                merged.username(),
                                merged.email(),
                                merged.phone(),
                                merged.firstName(),
                                merged.lastName(),
                                merged.service(),
                                merged.roles(),
                                merged.permissions(),
                                merged.photoUrl(),
                                merged.companyName(),
                                d.licenceNumber(),
                                d.assignedVehicleId() != null
                                    ? d.assignedVehicleId().toString()
                                    : null,
                                merged.isActive(),
                                merged.lastLoginAt()
                            )
                        )
                        .defaultIfEmpty(merged);
                }
                return Mono.just(merged);
            });
    }
}
