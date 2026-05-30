package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Adaptateur de simulation pour le développement local.
 * Permet de tester l'application sans dépendre du service d'authentification distant.
 */
@Slf4j
public class FakeAuthAdapter implements AuthPort {

    private static final UUID FAKE_ADMIN_ID = UUID.fromString("8a1f5e2c-3d4b-4c6a-9f8e-123456789abc");

    @Override
    public Mono<AuthResponse> login(String identifier, String password) {
        log.info("🛠 MODE FAKE AUTH : Login pour {}", identifier);

        // On renvoie un token qui contient le nom de l'utilisateur pour pouvoir le retrouver plus tard
        String fakeToken = "fake-token-" + identifier;

        UserDetail user = resolveUserFromToken(fakeToken);
        return Mono.just(new AuthResponse(fakeToken, "fake-refresh-token", user));
    }

    @Override
    public Mono<UserDetail> getUserProfile(String token) {
        // CORRECTION : On déduit l'utilisateur depuis le token au lieu de renvoyer toujours l'admin
        return Mono.just(resolveUserFromToken(token));
    }

    @Override
    public Mono<UserDetail> getUserById(UUID userId, String token) {
        log.info("🛠 MODE FAKE AUTH : Récupération user par ID {}", userId);
        return Mono.just(createFakeUser(
                userId,
                "user_" + userId.toString().substring(0, 5),
                "user@yowyob.test",
                "FLEET_MANAGER"
        ));
    }

    // --- Logique interne pour simuler l'intelligence du token ---
    private UserDetail resolveUserFromToken(String token) {
        // Format attendu: "fake-token-username" ou "Bearer fake-token-username"
        String cleanToken = token.replace("Bearer ", "");
        String username = cleanToken.replace("fake-token-", "");

        // Si le token ne suit pas le format, on fallback sur admin
        if (username.equals(cleanToken)) {
            username = "super_admin";
        }

        String role = username.contains("admin") ? "FLEET_ADMIN" : "FLEET_MANAGER";
        UUID userId = UUID.nameUUIDFromBytes(username.getBytes());

        return createFakeUser(userId, username, username + "@yowyob.com", role);
    }

    // ---------------------------------------------------------
    // Méthodes standards inchangées
    // ---------------------------------------------------------

    @Override
    public Mono<AuthResponse> registerInRemote(AuthUseCase.RegisterCommand command) {
        log.info("🛠 MODE FAKE AUTH : Inscription pour {}", command.username());
        UUID newUserId = UUID.randomUUID();
        UserDetail newUser = new UserDetail(
                newUserId, command.username(), command.email(), command.phone(),
                command.firstName(), command.lastName(), "FLEET_MANAGEMENT",
                command.roles(), List.of("fleet:read", "fleet:write"),
                "https://i.pravatar.cc/150?u=" + newUserId, null, null, null, true,null
        );
        return Mono.just(new AuthResponse("fake-token-" + command.username(), "fake-refresh", newUser));
    }

    @Override
    public Flux<UserDetail> getUsersByService(String serviceName, String token) {
        return Flux.just(
                createFakeUser(UUID.randomUUID(), "manager_1", "m1@yowyob.com", "FLEET_MANAGER"),
                createFakeUser(UUID.randomUUID(), "manager_2", "m2@yowyob.com", "FLEET_MANAGER")
        );
    }

    @Override
    public Flux<UserDetail> getAllUsers(String token) {
        return null;
    }

    @Override
    public Mono<UserDetail> updateUserProfile(UUID userId, String token, AuthUseCase.UpdateProfileCommand command) {
        return Mono.empty(); // Mock
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
        return null;
    }

    @Override
    public Mono<Void> updateProfilePicture(UUID userId, String token, AuthUseCase.FileContent file) {
        return Mono.empty();
    }

    @Override
    public Mono<AuthResponse> refresh(String refreshToken) {
        return Mono.empty(); // Mock
    }

    @Override
    public Mono<Boolean> roleExists(String roleName) {
        return Mono.just(true);
    }

    @Override
    public Mono<Void> createRole(String roleName) {
        return Mono.empty();
    }

    private UserDetail createFakeUser(UUID id, String username, String email, String role) {
        return new UserDetail(
                id, username, email, "+237600000000", "Fake", "User", "FLEET_MANAGEMENT",
                List.of(role), List.of("fleet:read", "fleet:write", "fleet:admin"),
                "https://i.pravatar.cc/150?u=" + id, null, null, null,true, null
        );
    }
}