package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.in.ManageDriverUseCase;
import com.yowyob.fleet.domain.ports.out.*;
import com.yowyob.fleet.domain.exception.AuthException;
import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

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
    public Mono<AuthPort.AuthResponse> login(String identifier, String password) {
        return authPort.login(identifier, password)
                .onErrorResume(e -> {
                  // On ne transforme que si c'est une erreur d'authentification pure (401)
                    if (e instanceof AuthException ae && ae.getStatus() == HttpStatus.UNAUTHORIZED) {
                        return Mono.error(AuthException.invalidCredentials()); // AUTH_001
                    }
                    // Pour toute autre erreur (500, 403 distant, etc.), on laisse passer tel quel
                    return Mono.error(e);
                })
                .flatMap(response -> pullSyncLocalUser(response.user())
                        .then(ensureRoleProfileExists(response.user()))
                        .then(checkUserAccess(response.user().id()))
                        .thenReturn(response));
    }

    @Override
    public Mono<AuthPort.AuthResponse> register(RegisterCommand command) {
        return ensureRolesExist(command.roles())
                .then(authPort.registerInRemote(command))
                 // --- AJOUT ICI ---
                .onErrorResume(e -> {
                    if (e instanceof AuthException ae && ae.getBusinessCode().equals("AUTH_004")) { // 409 Conflict
                        return userRepo.findByUsername(command.username()) // On cherche en local
                                .flatMap(u -> {
                                    if (!u.isActive()) {
                                        return Mono.<AuthPort.AuthResponse>error(new AuthException(
                                            "Ce compte est désactivé. Veuillez contacter un administrateur pour le réactiver.", 
                                            HttpStatus.FORBIDDEN, "AUTH_007"));
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
                    Mono<Void> photoFlow = (command.photo() != null) ?
                            this.updateProfilePicture(userId, token, command.photo())
                                    .onErrorResume(e -> {
                                        log.warn("📸 Photo ignorée au register suite à erreur distante.");
                                        return Mono.empty();
                                    }) : Mono.empty();

                    // 2. CHAINAGE : Photo -> Fetch Final -> Sync Local
                    return photoFlow
                            .then(authPort.getUserById(userId, token))
                            .flatMap(freshUser -> pullSyncLocalUser(freshUser)
                                    .then(ensureRoleProfileExists(freshUser))
                                    .thenReturn(new AuthPort.AuthResponse(token, res.refreshToken(), freshUser)));
                });
    }

    @Override
    public Mono<AuthPort.UserDetail> me(String token) {
        return authPort.getUserProfile(token)
                .flatMap(summary -> authPort.getUserById(summary.id(), token))
                .flatMap(remote -> pullSyncLocalUser(remote)
                        .then(ensureRoleProfileExists(remote)) // SELF-HEALING
                        .thenReturn(remote))
                .flatMap(this::enrichWithLocalData);
    }

    /**
     * FONCTION CENTRALE : Garantit que Hassana voit ses managers/drivers en DB locale.
     */
    private Mono<Void> ensureRoleProfileExists(AuthPort.UserDetail user) {
        if (user.roles().contains("FLEET_MANAGER")) {
            return managerRepo.existsById(user.id())
                    .flatMap(exists -> exists ? Mono.empty() : managerPort.createProfile(user.id(), "Société de " + user.lastName()));
        }
        if (user.roles().contains("FLEET_DRIVER")) {
            return driverPort.findById(user.id())
                    .flatMap(exists -> Mono.<Void>empty()) // Existe déjà
                    .switchIfEmpty(Mono.defer(() -> {
                        Driver d = new Driver(user.id(), null, "PENDING-" + user.id().toString().substring(0, 8), "ACTIVE", null, "");
                        return driverPort.save(d).then();
                    }));
        }
        return Mono.empty();
    }

    // --- AUTRES MÉTHODES (SYCHRONISÉES) ---

    @Override
    public Mono<AuthPort.AuthResponse> refreshToken(String refreshToken) {
        return authPort.refresh(refreshToken)
                .flatMap(response -> pullSyncLocalUser(response.user())
                        .then(ensureRoleProfileExists(response.user()))
                        .thenReturn(response));
    }

    @Override
    public Mono<AuthPort.UserDetail> updateProfile(UUID userId, String token, UpdateProfileCommand command) {
        return authPort.updateUserProfile(userId, token, command)
                .flatMap(remote -> pullSyncLocalUser(remote).thenReturn(remote))
                .flatMap(this::enrichWithLocalData);
    }

    @Override
    public Mono<Void> updateProfilePicture(UUID userId, String token, FileContent file) {
        return authPort.updateProfilePicture(userId, token, file)
                .delayElement(Duration.ofMillis(600)) // Sécurité stockage
                .then(authPort.getUserById(userId, token))
                .flatMap(this::pullSyncLocalUser);
    }

    @Override
    @Transactional
    public Mono<Void> deleteAccount(UUID userId, String token) {
        return authPort.getUserProfile(token)
            .flatMap(user -> {
                if (user.roles().contains("FLEET_MANAGER")) {
                    return fleetRepo.findAllByManagerId(userId).hasElements()
                        .flatMap(has -> has ? Mono.<Void>error(new AuthException("Supprimez vos flottes d'abord.", HttpStatus.CONFLICT, "ACC_001")) : Mono.empty());
                }
                if (user.roles().contains("FLEET_DRIVER")) {
                    return driverUseCase.unassignVehicle(userId, userId);
                }
                return Mono.<Void>empty();
            })
            .then(userRepo.findById(userId))
            .flatMap(local -> {
                local.setActive(false);
                local.setDeletedAt(Instant.now());
                local.setNew(false);
                return userRepo.save(local);
            })
            .then(authPort.moveUserToService(userId, "DELETED_USER", token));
    }

    private Mono<Void> pullSyncLocalUser(AuthPort.UserDetail remote) {
        return userRepo.findById(remote.id())
                .flatMap(local -> {
                    local.setUsername(remote.username());
                    local.setEmail(remote.email());
                    local.setFirstName(remote.firstName());
                    local.setLastName(remote.lastName());
                    local.setPhotoUrl(remote.photoUrl());
                    local.setNew(false);
                    return userRepo.save(local);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    UserLocalEntity n = UserLocalEntity.builder().id(remote.id()).username(remote.username()).email(remote.email())
                            .firstName(remote.firstName()).lastName(remote.lastName()).photoUrl(remote.photoUrl())
                            .isActive(true).lastLoginAt(Instant.now()).build();
                    n.setNew(true);
                    return userRepo.save(n);
                })).then();
    }

    private Mono<Void> checkUserAccess(UUID userId) {
        return userRepo.findById(userId).flatMap(u -> {
            if (u.getDeletedAt() != null) return Mono.error(AuthException.accountDeleted());
            if (!u.isActive()) return Mono.error(AuthException.accountLocked());
            return Mono.empty();
        });
    }

    @Override public Mono<Void> changePassword(UUID u, String t, String c, String n) { return authPort.changePassword(u, t, c, n); }

    private Mono<Void> ensureRolesExist(List<String> roles) {
        return Flux.fromIterable(roles).flatMap(r -> authPort.roleExists(r).flatMap(ex -> !ex ? authPort.createRole(r) : Mono.empty())).then();
    }

    private Mono<Void> createLocalProfile(AuthPort.UserDetail user) {
        return ensureRoleProfileExists(user); // Réutilisation pour éviter les doublons
    }

   private Mono<AuthPort.UserDetail> enrichWithLocalData(AuthPort.UserDetail remote) {
        if (remote.roles().contains("FLEET_MANAGER")) {
            return managerPort.getCompanyName(remote.id())
                    .map(c -> new AuthPort.UserDetail(
                        remote.id(), remote.username(), remote.email(), remote.phone(), 
                        remote.firstName(), remote.lastName(), remote.service(), 
                        remote.roles(), remote.permissions(), remote.photoUrl(), 
                        c, null, null, 
                        remote.isActive() ,
                        remote.lastLoginAt()
                    ))
                    .defaultIfEmpty(remote);
        }
        if (remote.roles().contains("FLEET_DRIVER")) {
            return driverPort.findById(remote.id())
                    .map(d -> new AuthPort.UserDetail(
                        remote.id(), remote.username(), remote.email(), remote.phone(), 
                        remote.firstName(), remote.lastName(), remote.service(), 
                        remote.roles(), remote.permissions(), remote.photoUrl(), 
                        null, d.licenceNumber(), d.assignedVehicleId() != null ? d.assignedVehicleId().toString() : null, 
                        remote.isActive(),remote.lastLoginAt()
                    ))
                    .defaultIfEmpty(remote);
        }
        return Mono.just(remote);
    }
}