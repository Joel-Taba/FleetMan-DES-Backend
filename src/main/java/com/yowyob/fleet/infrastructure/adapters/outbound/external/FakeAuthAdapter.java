package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.exception.AuthException;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.config.bootstrap.DemoTestAccounts;
import com.yowyob.fleet.infrastructure.config.bootstrap.DemoTestAccounts.Account;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import com.yowyob.fleet.application.service.LocalFileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.nio.file.Path;
import java.nio.file.Files;

/**
 * Adaptateur de simulation pour le développement local (profil demo).
 * Comptes prédéfinis — voir IDENTIFIANTS_TEST.md à la racine du dépôt.
 */
@Slf4j
public class FakeAuthAdapter implements AuthPort {

    @Autowired
    private UserLocalR2dbcRepository userRepo;

    @Autowired
    private DriverR2dbcRepository driverRepo;

    @Autowired
    private FleetManagerR2dbcRepository managerRepo;

    @Autowired
    private VehicleLocalR2dbcRepository vehicleRepo;

    @Autowired
    private LocalFileStorageService storageService;

    private static final java.util.Map<String, DemoTestAccounts.Account> registeredAccounts = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<UUID, DemoTestAccounts.Account> registeredAccountsById = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, String> registeredPasswords = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<UUID, String> customPhotoUrls = new java.util.concurrent.ConcurrentHashMap<>();

    private Mono<UserDetail> enrichPhotoUrl(UserDetail base) {
        if (userRepo == null)
            return Mono.just(base);
        return userRepo.findByKernelId(base.id())
                .switchIfEmpty(Mono.defer(() -> userRepo.findById(base.id())))
                .switchIfEmpty(Mono.defer(() -> {
                    var acc = DemoTestAccounts.findById(base.id());
                    if (acc.isPresent()) {
                        return userRepo.findByEmail(acc.get().email());
                    }
                    return Mono.empty();
                }))
                .map(userLocal -> {
                    if (userLocal.getPhotoUrl() != null && !userLocal.getPhotoUrl().isEmpty()) {
                        customPhotoUrls.put(base.id(), userLocal.getPhotoUrl());
                        return new UserDetail(
                                base.id(), base.username(), base.email(), base.phone(),
                                base.firstName(), base.lastName(), base.service(), base.roles(),
                                base.permissions(), userLocal.getPhotoUrl(), base.companyName(),
                                base.licenceNumber(), base.vehicleId(), base.isActive(),
                                base.lastLoginAt());
                    }
                    return base;
                })
                .defaultIfEmpty(base);
    }

    @Override
    public Mono<AuthResponse> login(String identifier, String password) {
        log.info("🛠 MODE FAKE AUTH : Login pour {}", identifier);
        return Mono.defer(() -> {
            String normId = identifier.trim().toLowerCase();
            var accountOpt = DemoTestAccounts.findByIdentifier(identifier);
            if (accountOpt.isPresent()) {
                if (!DemoTestAccounts.isDemoPassword(password)) {
                    return Mono.error(AuthException.invalidCredentials());
                }
                var acc = accountOpt.get();
                return enrichPhotoUrl(toUserDetail(acc))
                        .map(enriched -> new AuthResponse(tokenFor(acc), "fake-refresh-token", enriched));
            }

            // Vérification des comptes enregistrés dynamiquement
            var dynamicAcc = registeredAccounts.get(normId);
            if (dynamicAcc != null) {
                String expectedPassword = registeredPasswords.get(normId);
                if (expectedPassword != null && (expectedPassword.equals(password) || "FleetMan2026!".equals(password)
                        || "Nehemie@123".equals(password) || "Frank@123".equals(password)
                        || "Fank@123".equals(password))) {
                    return enrichPhotoUrl(toUserDetail(dynamicAcc))
                            .map(enriched -> new AuthResponse("fake-token-" + dynamicAcc.email(), "fake-refresh-token",
                                    enriched));
                }
            }

            // Fallback de secours sur la base de données locale si absent du cache (ex:
            // redémarrage serveur)
            if (userRepo != null) {
                return userRepo.findByEmail(normId)
                        .switchIfEmpty(Mono.defer(() -> userRepo.findByUsername(normId)))
                        .flatMap(userLocal -> {
                            Mono<List<String>> rolesMono;
                            if (driverRepo != null && managerRepo != null) {
                                rolesMono = driverRepo.existsById(userLocal.getId())
                                        .flatMap(isDriver -> {
                                            if (isDriver) {
                                                return Mono.just(List.of("FLEET_DRIVER"));
                                            }
                                            return managerRepo.existsById(userLocal.getId())
                                                    .map(isManager -> {
                                                        if (isManager) {
                                                            return List.of("FLEET_MANAGER");
                                                        }
                                                        return List.of("FLEET_DRIVER"); // par défaut
                                                    });
                                        });
                            } else {
                                rolesMono = Mono.just(List.of("FLEET_DRIVER"));
                            }

                            return rolesMono.flatMap(roles -> {
                                DemoTestAccounts.Account acc = new DemoTestAccounts.Account(
                                        userLocal.getId(),
                                        userLocal.getUsername(),
                                        userLocal.getEmail(),
                                        userLocal.getFirstName(),
                                        userLocal.getLastName(),
                                        roles);
                                // Re-cache en mémoire pour la session actuelle
                                registeredAccounts.put(normId, acc);
                                registeredAccountsById.put(userLocal.getId(), acc);
                                registeredPasswords.put(normId, password);
                                return enrichPhotoUrl(toUserDetail(acc))
                                        .map(enriched -> new AuthResponse("fake-token-" + acc.email(),
                                                "fake-refresh-token", enriched));
                            });
                        })
                        .switchIfEmpty(Mono.error(AuthException.invalidCredentials()));
            }

            return Mono.error(AuthException.invalidCredentials());
        });
    }

    @Override
    public Mono<UserDetail> getUserProfile(String token) {
        UserDetail base = resolveUserFromToken(token);
        Mono<UserDetail> baseMono;
        if (base.roles().contains("FLEET_DRIVER")) {
            baseMono = vehicleRepo.findByCurrentDriverId(base.id())
                    .map(v -> v.getId().toString())
                    .next()
                    .flatMap(vId -> Mono.just(new UserDetail(
                            base.id(),
                            base.username(),
                            base.email(),
                            base.phone(),
                            base.firstName(),
                            base.lastName(),
                            base.service(),
                            base.roles(),
                            base.permissions(),
                            base.photoUrl(),
                            base.companyName(),
                            base.licenceNumber(),
                            vId,
                            base.isActive(),
                            base.lastLoginAt())))
                    .switchIfEmpty(Mono.defer(() -> {
                        return driverRepo.findById(base.id())
                                .map(driver -> {
                                    if (driver.getAssignedVehicleId() != null) {
                                        return new UserDetail(
                                                base.id(),
                                                base.username(),
                                                base.email(),
                                                base.phone(),
                                                base.firstName(),
                                                base.lastName(),
                                                base.service(),
                                                base.roles(),
                                                base.permissions(),
                                                base.photoUrl(),
                                                base.companyName(),
                                                base.licenceNumber(),
                                                driver.getAssignedVehicleId().toString(),
                                                base.isActive(),
                                                base.lastLoginAt());
                                    }
                                    return base;
                                })
                                .defaultIfEmpty(base);
                    }));
        } else {
            baseMono = Mono.just(base);
        }
        return baseMono.flatMap(this::enrichPhotoUrl);
    }

    @Override
    public Mono<UserDetail> getUserById(UUID userId, String token) {
        Mono<UserDetail> baseMono;
        var staticAcc = DemoTestAccounts.findById(userId);
        if (staticAcc.isPresent()) {
            baseMono = Mono.just(toUserDetail(staticAcc.get()));
        } else {
            var dynamicAcc = registeredAccountsById.get(userId);
            if (dynamicAcc != null) {
                baseMono = Mono.just(toUserDetail(dynamicAcc));
            } else {
                baseMono = Mono.just(resolveUserFromToken(token));
            }
        }
        return baseMono.flatMap(base -> {
            if (base.roles().contains("FLEET_DRIVER")) {
                return vehicleRepo.findByCurrentDriverId(base.id())
                        .map(v -> v.getId().toString())
                        .next()
                        .flatMap(vId -> Mono.just(new UserDetail(
                                base.id(),
                                base.username(),
                                base.email(),
                                base.phone(),
                                base.firstName(),
                                base.lastName(),
                                base.service(),
                                base.roles(),
                                base.permissions(),
                                base.photoUrl(),
                                base.companyName(),
                                base.licenceNumber(),
                                vId,
                                base.isActive(),
                                base.lastLoginAt())))
                        .switchIfEmpty(Mono.defer(() -> {
                            return driverRepo.findById(base.id())
                                    .map(driver -> {
                                        if (driver.getAssignedVehicleId() != null) {
                                            return new UserDetail(
                                                    base.id(),
                                                    base.username(),
                                                    base.email(),
                                                    base.phone(),
                                                    base.firstName(),
                                                    base.lastName(),
                                                    base.service(),
                                                    base.roles(),
                                                    base.permissions(),
                                                    base.photoUrl(),
                                                    base.companyName(),
                                                    base.licenceNumber(),
                                                    driver.getAssignedVehicleId().toString(),
                                                    base.isActive(),
                                                    base.lastLoginAt());
                                        }
                                        return base;
                                    })
                                    .defaultIfEmpty(base);
                        }));
            }
            return Mono.just(base);
        }).flatMap(this::enrichPhotoUrl);
    }

    private UserDetail resolveUserFromToken(String token) {
        String clean = token.replace("Bearer ", "").trim();
        String suffix = clean.replace("fake-token-", "").trim().toLowerCase();

        var staticAcc = DemoTestAccounts.findByIdentifier(suffix)
                .or(() -> DemoTestAccounts.findById(UUID.nameUUIDFromBytes(suffix.getBytes())));
        if (staticAcc.isPresent()) {
            return this.toUserDetail(staticAcc.get());
        }

        var dynamicAcc = registeredAccounts.get(suffix);
        if (dynamicAcc != null) {
            return this.toUserDetail(dynamicAcc);
        }

        return toUserDetail(DemoTestAccounts.findByIdentifier("superadmin@fleetman.cm").orElseThrow());
    }

    private String tokenFor(DemoTestAccounts.Account account) {
        return "fake-token-" + account.email();
    }

    private List<String> cleanRoles(List<String> rawRoles) {
        if (rawRoles == null)
            return List.of();
        return rawRoles.stream()
                .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                .toList();
    }

    private UserDetail toUserDetail(DemoTestAccounts.Account account) {
        List<String> normalizedRoles = cleanRoles(account.roles());
        String photoUrl = customPhotoUrls.get(account.id());
        if (photoUrl == null) {
            photoUrl = "https://i.pravatar.cc/150?u=" + account.id();
        }
        return new UserDetail(
                account.id(),
                account.username(),
                account.email(),
                "+237600000000",
                account.firstName(),
                account.lastName(),
                normalizedRoles.contains("FLEET_DRIVER") ? "DRIVERS" : "FLEET_MANAGEMENT",
                normalizedRoles,
                List.of("fleet:read", "fleet:write", "fleet:admin"),
                photoUrl,
                null,
                null,
                null,
                true,
                null);
    }

    @Override
    public Mono<AuthResponse> registerInRemote(AuthUseCase.RegisterCommand command) {
        UUID newUserId = UUID.randomUUID();
        List<String> normalizedRoles = cleanRoles(command.roles());

        String defaultPhotoUrl = "https://i.pravatar.cc/150?u=" + newUserId;
        String photoUrl = customPhotoUrls.getOrDefault(newUserId, defaultPhotoUrl);

        UserDetail newUser = new UserDetail(
                newUserId, command.username(), command.email(), command.phone(),
                command.firstName(), command.lastName(),
                normalizedRoles.contains("FLEET_DRIVER") ? "DRIVERS" : "FLEET_MANAGEMENT",
                normalizedRoles, List.of("fleet:read", "fleet:write"),
                photoUrl, null, null, null, true, null);

        DemoTestAccounts.Account newAcc = new DemoTestAccounts.Account(
                newUserId, command.username(), command.email(),
                command.firstName(), command.lastName(), normalizedRoles);

        registeredAccounts.put(command.username().toLowerCase().trim(), newAcc);
        registeredAccounts.put(command.email().toLowerCase().trim(), newAcc);
        registeredAccountsById.put(newUserId, newAcc);
        if (command.password() != null) {
            registeredPasswords.put(command.username().toLowerCase().trim(), command.password());
            registeredPasswords.put(command.email().toLowerCase().trim(), command.password());
        }

        return enrichPhotoUrl(newUser)
                .map(enriched -> new AuthResponse("fake-token-" + command.email(), "fake-refresh", enriched));
    }

    @Override
    public Flux<UserDetail> getUsersByService(String serviceName, String token) {
        java.util.List<UserDetail> list = new java.util.ArrayList<>();
        java.util.Set<UUID> ids = new java.util.HashSet<>();

        for (DemoTestAccounts.Account a : DemoTestAccounts.getAll()) {
            UserDetail ud = toUserDetail(a);
            if (ud.service().equalsIgnoreCase(serviceName)) {
                if (ids.add(ud.id())) {
                    list.add(ud);
                }
            }
        }

        for (DemoTestAccounts.Account a : registeredAccounts.values()) {
            UserDetail ud = toUserDetail(a);
            if (ud.service().equalsIgnoreCase(serviceName)) {
                if (ids.add(ud.id())) {
                    list.add(ud);
                }
            }
        }

        if (userRepo != null && managerRepo != null && driverRepo != null) {
            Flux<UserDetail> dbFlux;
            if ("FLEET_MANAGEMENT".equalsIgnoreCase(serviceName)) {
                dbFlux = managerRepo.findAll()
                        .flatMap(m -> userRepo.findById(m.getId())
                                .map(u -> new UserDetail(
                                        u.getId(),
                                        u.getUsername(),
                                        u.getEmail(),
                                        "+237600000000",
                                        u.getFirstName(),
                                        u.getLastName(),
                                        "FLEET_MANAGEMENT",
                                        List.of("FLEET_MANAGER"),
                                        List.of(),
                                        m.getCompanyName(),
                                        null,
                                        null,
                                        null,
                                        u.isActive(),
                                        u.getLastLoginAt())));
            } else if ("DRIVERS".equalsIgnoreCase(serviceName)) {
                dbFlux = driverRepo.findAll()
                        .flatMap(d -> userRepo.findById(d.getId())
                                .map(u -> new UserDetail(
                                        u.getId(),
                                        u.getUsername(),
                                        u.getEmail(),
                                        "+237600000000",
                                        u.getFirstName(),
                                        u.getLastName(),
                                        "DRIVERS",
                                        List.of("FLEET_DRIVER"),
                                        List.of(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        u.isActive(),
                                        u.getLastLoginAt())));
            } else {
                dbFlux = Flux.empty();
            }

            return Flux.fromIterable(list)
                    .concatWith(dbFlux)
                    .filter(ud -> ids.add(ud.id()));
        }

        return Flux.fromIterable(list);
    }

    @Override
    public Flux<UserDetail> getAllUsers(String token) {
        return Flux.empty();
    }

    @Override
    public Mono<UserDetail> updateUserProfile(UUID userId, String token, AuthUseCase.UpdateProfileCommand command) {
        return DemoTestAccounts.findById(userId)
                .map(acc -> Mono.just(toUserDetail(acc)))
                .orElse(Mono.empty());
    }

    @Override
    public Mono<Void> changePassword(UUID userId, String token, String currentPwd, String newPwd) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> deleteRemoteAccount(UUID userId, String token) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> moveUserToService(UUID userId, String newServiceName, String token) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> updateProfilePicture(UUID userId, String token, AuthUseCase.FileContent file) {
        return Mono.fromCallable(() -> {
            String originalName = file.filename();
            String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : ".jpg";
            String storedName = "avatar-" + userId + ext;
            Path resolved = storageService.resolve("");
            Path target = resolved.resolve(storedName);
            Files.write(target, file.data());

            String publicUrl = "/api/v1/files/" + storedName;
            customPhotoUrls.put(userId, publicUrl);
            log.info("Saved profile photo for user {} to {}, url: {}", userId, target, publicUrl);
            return publicUrl;
        })
                .flatMap(publicUrl -> {
                    if (userRepo != null) {
                        return userRepo.findByKernelId(userId)
                                .switchIfEmpty(Mono.defer(() -> userRepo.findById(userId)))
                                .switchIfEmpty(Mono.defer(() -> {
                                    var acc = DemoTestAccounts.findById(userId);
                                    if (acc.isPresent()) {
                                        return userRepo.findByEmail(acc.get().email());
                                    }
                                    return Mono.empty();
                                }))
                                .flatMap(userLocal -> {
                                    userLocal.setPhotoUrl(publicUrl);
                                    userLocal.setNew(false);
                                    return userRepo.save(userLocal);
                                })
                                .then();
                    }
                    return Mono.empty();
                });
    }

    @Override
    public Mono<AuthResponse> refresh(String refreshToken) {
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> roleExists(String roleName) {
        return Mono.just(true);
    }

    @Override
    public Mono<Void> createRole(String roleName) {
        return Mono.empty();
    }

    @Override
    public Mono<AuthResponse> selectContext(String selectionToken, String contextId, UUID organizationId) {
        String email = "admin@fleetman.cm";
        if (selectionToken != null && selectionToken.contains(":")) {
            email = selectionToken.substring(selectionToken.indexOf(":") + 1);
        }
        String password = "FleetMan2026!";
        if (email.contains("nehemie")) {
            password = "Nehemie@123";
        } else if (email.contains("frank")) {
            password = "Frank@123";
        }
        return this.login(email, password);
    }
}
