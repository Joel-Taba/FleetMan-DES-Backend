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
import java.util.UUID;

/**
 * Adaptateur de simulation pour le développement local (profil demo).
 * Comptes prédéfinis — voir IDENTIFIANTS_TEST.md à la racine du dépôt.
 */
@Slf4j
public class FakeAuthAdapter implements AuthPort {

    private static final java.util.Map<String, DemoTestAccounts.Account> registeredAccounts = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<UUID, DemoTestAccounts.Account> registeredAccountsById = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, String> registeredPasswords = new java.util.concurrent.ConcurrentHashMap<>();

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
                return Mono.just(new AuthResponse(tokenFor(acc), "fake-refresh-token", toUserDetail(acc)));
            }

            // Vérification des comptes enregistrés dynamiquement
            var dynamicAcc = registeredAccounts.get(normId);
            if (dynamicAcc != null) {
                String expectedPassword = registeredPasswords.get(normId);
                if (expectedPassword != null && expectedPassword.equals(password)) {
                    return Mono.just(new AuthResponse("fake-token-" + dynamicAcc.email(), "fake-refresh-token",
                            toUserDetail(dynamicAcc)));
                }
            }

            return Mono.error(AuthException.invalidCredentials());
        });
    }

    @Override
    public Mono<UserDetail> getUserProfile(String token) {
        return Mono.just(resolveUserFromToken(token));
    }

    @Override
    public Mono<UserDetail> getUserById(UUID userId, String token) {
        var staticAcc = DemoTestAccounts.findById(userId);
        if (staticAcc.isPresent()) {
            return Mono.just(toUserDetail(staticAcc.get()));
        }
        var dynamicAcc = registeredAccountsById.get(userId);
        if (dynamicAcc != null) {
            return Mono.just(toUserDetail(dynamicAcc));
        }
        return Mono.just(resolveUserFromToken(token));
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

    private UserDetail toUserDetail(DemoTestAccounts.Account account) {
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
                null);
    }

    @Override
    public Mono<AuthResponse> registerInRemote(AuthUseCase.RegisterCommand command) {
        UUID newUserId = UUID.randomUUID();
        UserDetail newUser = new UserDetail(
                newUserId, command.username(), command.email(), command.phone(),
                command.firstName(), command.lastName(), "FLEET_MANAGEMENT",
                command.roles(), List.of("fleet:read", "fleet:write"),
                "https://i.pravatar.cc/150?u=" + newUserId, null, null, null, true, null);

        DemoTestAccounts.Account newAcc = new DemoTestAccounts.Account(
                newUserId, command.username(), command.email(),
                command.firstName(), command.lastName(), command.roles());

        registeredAccounts.put(command.username().toLowerCase().trim(), newAcc);
        registeredAccounts.put(command.email().toLowerCase().trim(), newAcc);
        registeredAccountsById.put(newUserId, newAcc);
        if (command.password() != null) {
            registeredPasswords.put(command.username().toLowerCase().trim(), command.password());
            registeredPasswords.put(command.email().toLowerCase().trim(), command.password());
        }

        return Mono.just(new AuthResponse("fake-token-" + command.email(), "fake-refresh", newUser));
    }

    @Override
    public Flux<UserDetail> getUsersByService(String serviceName, String token) {
        return Flux.just(
                toUserDetail(DemoTestAccounts.findByIdentifier("manager@fleetman.cm").orElseThrow()),
                toUserDetail(DemoTestAccounts.findByIdentifier("admin@fleetman.cm").orElseThrow()));
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
