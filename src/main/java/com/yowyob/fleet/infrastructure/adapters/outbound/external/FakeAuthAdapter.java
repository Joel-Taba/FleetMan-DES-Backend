package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.exception.AuthException;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.config.bootstrap.DemoTestAccounts;
import com.yowyob.fleet.infrastructure.config.bootstrap.DemoTestAccounts.Account;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptateur de simulation pour le développement local (profil fake-auth).
 * Comptes prédéfinis — voir application-fake-auth.yml / IDENTIFIANTS_TEST.md.
 * Accepte aussi les mots de passe réinitialisés via forgot-password (override en mémoire).
 */
@Slf4j
public class FakeAuthAdapter implements AuthPort {

    /** Mot de passe override par email (après reset-password). */
    private final Map<String, String> passwordOverrides = new ConcurrentHashMap<>();

    /** Comptes créés dynamiquement via register (signup / tests massifs). */
    private final Map<String, Account> dynamicAccounts = new ConcurrentHashMap<>();
    private final Map<UUID, Account> dynamicById = new ConcurrentHashMap<>();
    /** Profils mis à jour (compte demo ou dynamique). */
    private final Map<UUID, Account> profileOverrides = new ConcurrentHashMap<>();

    @Override
    public Mono<AuthResponse> login(String identifier, String password) {
        log.info("🛠 MODE FAKE AUTH : Login pour {}", identifier);
        return Mono.defer(() -> {
            Account acc = resolveAccount(identifier);
            if (acc == null) {
                return Mono.error(AuthException.invalidCredentials());
            }
            if (!passwordMatches(acc, password)) {
                return Mono.error(AuthException.invalidCredentials());
            }
            Account effective = profileOverrides.getOrDefault(acc.id(), acc);
            return Mono.just(new AuthResponse(tokenFor(effective), "fake-refresh-token", toUserDetail(effective)));
        });
    }

    private Account resolveAccount(String identifier) {
        Account dyn = dynamicAccounts.get(normalize(identifier));
        if (dyn != null) {
            return profileOverrides.getOrDefault(dyn.id(), dyn);
        }
        return DemoTestAccounts.findByIdentifier(identifier)
                .map(acc -> profileOverrides.getOrDefault(acc.id(), acc))
                .orElse(null);
    }

    /** Enregistre un nouveau mot de passe pour un email demo (après reset). */
    public boolean overridePassword(String email, String newPassword) {
        Account acc = resolveAccount(email);
        if (acc == null) {
            return false;
        }
        passwordOverrides.put(normalize(acc.email()), newPassword);
        return true;
    }

    private boolean passwordMatches(Account acc, String password) {
        String override = passwordOverrides.get(normalize(acc.email()));
        if (override != null) {
            return override.equals(password);
        }
        if (DemoTestAccounts.findById(acc.id()).isPresent()) {
            return DemoTestAccounts.isDemoPassword(password);
        }
        // Compte dynamique : mdp stocké dans overrides au register
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    @Override
    public Mono<AuthResponse> selectContext(String selectionToken, String contextId, UUID organizationId) {
        return login("manager@fleetman.cm", DemoTestAccounts.DEMO_PASSWORD);
    }

    @Override
    public Mono<UserDetail> getUserProfile(String token) {
        return resolveUserFromToken(token)
                .map(Mono::just)
                .orElseGet(() -> Mono.error(AuthException.tokenExpired()));
    }

    @Override
    public Mono<UserDetail> getUserById(UUID userId, String token) {
        Account overridden = profileOverrides.get(userId);
        if (overridden != null) {
            return Mono.just(toUserDetail(overridden));
        }
        return DemoTestAccounts.findById(userId)
                .or(() -> Optional.ofNullable(dynamicById.get(userId)))
                .map(acc -> Mono.just(toUserDetail(acc)))
                .orElseGet(() -> resolveUserFromToken(token)
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(AuthException.tokenExpired())));
    }

    private Optional<UserDetail> resolveUserFromToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String clean = token.replace("Bearer ", "").trim();
        if (!clean.startsWith("fake-token-")) {
            log.warn("🛠 MODE FAKE AUTH : token non reconnu (attendu fake-token-<email>)");
            return Optional.empty();
        }
        String suffix = clean.substring("fake-token-".length());
        Account acc = resolveAccount(suffix);
        if (acc != null) {
            return Optional.of(toUserDetail(profileOverrides.getOrDefault(acc.id(), acc)));
        }
        return DemoTestAccounts.findByIdentifier(suffix)
                .or(() -> DemoTestAccounts.findById(UUID.nameUUIDFromBytes(suffix.getBytes())))
                .map(a -> toUserDetail(profileOverrides.getOrDefault(a.id(), a)));
    }

    private String tokenFor(Account account) {
        return "fake-token-" + account.email();
    }

    private UserDetail toUserDetail(Account account) {
        return new UserDetail(
                account.id(),
                account.username(),
                account.email(),
                "+237600000000",
                account.firstName(),
                account.lastName(),
                "FLEET_MANAGEMENT",
                account.roles(),
                List.of("fleet:read", "fleet:write", "fleet:admin"),
                "https://i.pravatar.cc/150?u=" + account.id(),
                null,
                null,
                null,
                true,
                null
        );
    }

    @Override
    public Mono<AuthResponse> registerInRemote(AuthUseCase.RegisterCommand command) {
        UUID newUserId = UUID.randomUUID();
        Account acc = new Account(
                newUserId,
                command.username(),
                command.email(),
                command.firstName(),
                command.lastName(),
                command.roles() != null ? command.roles() : List.of("FLEET_MANAGER"),
                newUserId
        );
        dynamicAccounts.put(normalize(acc.email()), acc);
        dynamicAccounts.put(normalize(acc.username()), acc);
        dynamicById.put(newUserId, acc);
        passwordOverrides.put(normalize(acc.email()), command.password());

        UserDetail newUser = toUserDetail(acc);
        return Mono.just(new AuthResponse("fake-token-" + command.email(), "fake-refresh", newUser));
    }

    @Override
    public Flux<UserDetail> getUsersByService(String serviceName, String token) {
        return Flux.fromStream(java.util.stream.Stream.concat(
                DemoTestAccounts.all().stream(),
                dynamicById.values().stream()
        ).distinct())
                .map(this::toUserDetail);
    }

    @Override
    public Flux<UserDetail> getAllUsers(String token) {
        return getUsersByService("FLEET_MANAGEMENT", token);
    }

    @Override
    public Mono<UserDetail> updateUserProfile(UUID userId, String token, AuthUseCase.UpdateProfileCommand command) {
        Account existing = DemoTestAccounts.findById(userId)
                .orElseGet(() -> dynamicById.get(userId));
        if (existing == null) {
            // Essai via token
            existing = resolveAccount(token.replace("Bearer ", "").replace("fake-token-", "").trim());
        }
        if (existing == null) {
            return Mono.empty();
        }
        Account updated = new Account(
                existing.id(),
                existing.username(),
                command.email() != null && !command.email().isBlank() ? command.email() : existing.email(),
                command.firstName() != null ? command.firstName() : existing.firstName(),
                command.lastName() != null ? command.lastName() : existing.lastName(),
                existing.roles(),
                existing.kernelId()
        );
        profileOverrides.put(updated.id(), updated);
        if (dynamicById.containsKey(existing.id())) {
            dynamicById.put(updated.id(), updated);
            dynamicAccounts.put(normalize(updated.email()), updated);
            dynamicAccounts.put(normalize(updated.username()), updated);
            if (!normalize(existing.email()).equals(normalize(updated.email()))) {
                dynamicAccounts.remove(normalize(existing.email()));
                String pwd = passwordOverrides.remove(normalize(existing.email()));
                if (pwd != null) passwordOverrides.put(normalize(updated.email()), pwd);
            }
        }
        return Mono.just(toUserDetail(updated));
    }

    @Override
    public Mono<Void> changePassword(UUID userId, String token, String currentPwd, String newPwd) {
        Account acc = DemoTestAccounts.findById(userId)
                .orElseGet(() -> dynamicById.get(userId));
        if (acc == null) {
            acc = resolveAccount(token.replace("Bearer ", "").replace("fake-token-", "").trim());
        }
        if (acc == null) {
            return Mono.empty();
        }
        if (!passwordMatches(acc, currentPwd)) {
            return Mono.error(AuthException.invalidCredentials());
        }
        overridePassword(acc.email(), newPwd);
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
        return Mono.empty();
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
}
