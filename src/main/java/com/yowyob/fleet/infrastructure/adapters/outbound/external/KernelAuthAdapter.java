package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.domain.exception.AuthException;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.AuthController;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelAuthApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Adaptateur d'authentification branché sur le Kernel RT-Comops.
 * Activé par : application.auth.mode=kernel
 *
 * Flow login Kernel (2 étapes) :
 * 1. POST /api/auth/discover-contexts → selectionToken + contexts[]
 * 2. POST /api/auth/select-context → JWT RS256 (accessToken)
 *
 * Sécurité : headers X-Client-Id + X-Api-Key injectés automatiquement par le
 * WebClient.
 */
@Slf4j
@RequiredArgsConstructor
public class KernelAuthAdapter implements AuthPort {

    private final KernelAuthApiClient kernelClient;
    private final WebClient kernelWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${application.kernel.service:FLEET_MANAGEMENT}")
    private String serviceName;

    @Value("${application.kernel.organization-id:}")
    private String defaultOrgId;

    @Value("${application.geofence-system-user.username:system@fleetman.cm}")
    private String systemUsername;

    @Value("${application.geofence-system-user.password:SystemFleetMan2026!}")
    private String systemPassword;

    // ── Login (2 étapes) ──────────────────────────────────────────────────────

    @Override
    public Mono<AuthResponse> login(String identifier, String password) {
        log.info("🔑 [KERNEL AUTH] Login pour : {}", identifier);

        // Étape 1 : discover-contexts
        return kernelClient.discoverContexts(
                new KernelAuthApiClient.LoginRequest(identifier, password))
                .flatMap(resp -> {
                    if (!resp.success() || resp.data() == null) {
                        log.warn("❌ [KERNEL AUTH] discover-contexts failed: {}", resp.message());
                        return Mono.error(AuthException.invalidCredentials());
                    }

                    var discover = resp.data();
                    if (discover.contexts() == null || discover.contexts().isEmpty()) {
                        return Mono.error(AuthException.invalidCredentials());
                    }

                    // Prendre le premier contexte disponible (ou celui du service FLEET_MANAGEMENT)
                    var ctx = discover.contexts().stream()
                            .filter(c -> c.organizations() != null && c.organizations().stream()
                                    .anyMatch(o -> serviceName.equalsIgnoreCase(o.service())))
                            .findFirst()
                            .orElse(discover.contexts().get(0));

                    // Déterminer l'organizationId cible
                    UUID orgId = null;
                    if (ctx.organizations() != null && !ctx.organizations().isEmpty()) {
                        orgId = ctx.organizations().stream()
                                .filter(o -> serviceName.equalsIgnoreCase(o.service()))
                                .map(KernelAuthApiClient.OrganizationRef::organizationId)
                                .findFirst()
                                .orElse(ctx.organizations().get(0).organizationId());
                    }

                    log.debug("✅ [KERNEL AUTH] Context sélectionné: {} org={}", ctx.contextId(), orgId);

                    // Étape 2 : select-context
                    return kernelClient.selectContext(
                            new KernelAuthApiClient.SelectContextRequest(
                                    discover.selectionToken(),
                                    ctx.contextId(),
                                    orgId));
                })
                .map(resp -> {
                    if (!resp.success() || resp.data() == null) {
                        throw AuthException.invalidCredentials();
                    }
                    var session = resp.data().session();
                    return mapToAuthResponse(session);
                })
                .onErrorResume(WebClientResponseException.class, this::mapWebClientError)
                .doOnSuccess(r -> log.info("✅ [KERNEL AUTH] Login réussi : {}", identifier));
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────

    @Override
    public Mono<AuthResponse> refresh(String refreshToken) {
        return kernelClient.refreshToken(
                new KernelAuthApiClient.RefreshTokenRequest(refreshToken))
                .map(resp -> {
                    if (!resp.success() || resp.data() == null) {
                        throw AuthException.tokenExpired();
                    }
                    var r = resp.data();
                    // On reconstruit une AuthResponse avec le nouveau token
                    var fakeUser = new UserDetail(null, null, null, null, null, null,
                            serviceName, List.of(), List.of(), null, null, null, null, true, null);
                    return new AuthResponse(r.accessToken(), r.refreshToken(), fakeUser);
                })
                .onErrorResume(WebClientResponseException.class, this::mapWebClientError);
    }

    // ── Inscription ───────────────────────────────────────────────────────────

    @Override
    public Mono<AuthResponse> registerInRemote(AuthUseCase.RegisterCommand command) {
        log.info("📝 [KERNEL AUTH] Inscription : {}", command.email());
        return this.login(systemUsername, systemPassword)
                .flatMap(systemAuth -> {
                    String token = "Bearer " + systemAuth.accessToken();
                    return kernelClient.register(
                            token,
                            new KernelAuthApiClient.RegisterUserRequest(
                                    command.username(),
                                    command.email(),
                                    command.phone(),
                                    command.password(),
                                    "LOCAL"));
                })
                .flatMap(resp -> {
                    if (!resp.success() || resp.data() == null) {
                        return Mono.error(AuthException.generic(resp.message(), HttpStatus.BAD_REQUEST));
                    }
                    var user = resp.data();
                    UserDetail detail = new UserDetail(
                            user.id(), user.username(), user.email(), user.phoneNumber(),
                            command.firstName(), command.lastName(), serviceName,
                            command.roles(), List.of(),
                            null, null, null, null, true, null);
                    return Mono.just(new AuthResponse("", "", detail));
                })
                .onErrorResume(WebClientResponseException.class, this::mapWebClientError)
                .doOnSuccess(r -> log.info("✅ [KERNEL AUTH] Inscription réussie : {}", command.email()));
    }

    // ── Profil utilisateur ────────────────────────────────────────────────────

    /**
     * Extrait le profil utilisateur DIRECTEMENT depuis les claims JWT,
     * sans appel réseau vers le Kernel.
     *
     * Le JWT (RS256 signé par le Kernel) contient :
     * - sub : userId (UUID Kernel)
     * - permissions: rôles et permissions (source fiable)
     * - tid : tenantId
     * - exp : expiration
     */
    @Override
    public Mono<UserDetail> getUserProfile(String token) {
        try {
            String rawToken = token.startsWith("Bearer ") ? token.substring(7) : token;

            // Source des rôles = JWT (seule source fiable — /api/users/me ne porte pas les
            // permissions)
            List<String> authorities = extractAuthoritiesFromJwt(rawToken);
            List<String> roles = extractFleetRoles(authorities);

            String[] parts = rawToken.split("\\.");
            if (parts.length < 2) {
                log.warn("⚠️ [KERNEL AUTH] JWT malformé");
                return Mono.error(AuthException.tokenExpired());
            }

            String payloadJson = new String(
                    Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode payload = objectMapper.readTree(payloadJson);

            // Vérifier l'expiration
            long exp = payload.has("exp") ? payload.get("exp").asLong(0) : 0;
            long now = System.currentTimeMillis() / 1000;
            if (exp > 0 && now > exp) {
                log.warn("⚠️ [KERNEL AUTH] Token JWT expiré (exp={}, now={})", exp, now);
                return Mono.error(AuthException.tokenExpired());
            }

            String sub = payload.has("sub") ? payload.get("sub").asText() : null;

            UUID userId;
            try {
                userId = UUID.fromString(sub);
            } catch (Exception ex) {
                log.warn("⚠️ [KERNEL AUTH] sub non UUID: {}", sub);
                return Mono.error(AuthException.tokenExpired());
            }

            log.debug("✅ [KERNEL AUTH] getUserProfile depuis JWT : userId={}, roles={}", userId, roles);

            UserDetail detail = new UserDetail(
                    userId, sub, null, null,
                    null, null, serviceName,
                    roles, authorities,
                    null, null, null, null, true, null);

            return Mono.just(detail);

        } catch (Exception e) {
            log.error("❌ [KERNEL AUTH] Erreur lecture JWT : {}", e.getMessage());
            return Mono.error(AuthException.tokenExpired());
        }
    }

    @Override
    public Mono<UserDetail> getUserById(UUID userId, String token) {
        // Le Kernel ne propose pas de GET /api/users/{id} direct dans la spec
        // On utilise le profil connecté ou on retourne vide
        return getUserProfile(token)
                .filter(u -> u.id() != null && u.id().equals(userId))
                .switchIfEmpty(Mono.empty());
    }

    @Override
    public Flux<UserDetail> getUsersByService(String svcName, String token) {
        return kernelClient.listAdmins(ensureBearer(token))
                .flatMapMany(resp -> {
                    if (!resp.success() || resp.data() == null)
                        return Flux.empty();
                    return Flux.fromIterable(resp.data()).map(this::mapToUserDetail);
                })
                .onErrorResume(e -> {
                    log.warn("⚠️ [KERNEL AUTH] getUsersByService error: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    @Override
    public Flux<UserDetail> getAllUsers(String token) {
        return getUsersByService(serviceName, token);
    }

    // ── Nouvelles méthodes pour le flux multi-tenant ──────────────────────────

    public Mono<AuthController.DiscoverContextsResponse> discoverContexts(String principal, String password) {
        log.info("🔍 [KERNEL AUTH] discover-contexts pour: {}", principal);
        return kernelClient.discoverContexts(new KernelAuthApiClient.LoginRequest(principal, password))
                .map(resp -> {
                    if (!resp.success() || resp.data() == null) {
                        throw AuthException.invalidCredentials();
                    }
                    var data = resp.data();
                    var contexts = data.contexts() == null ? List.<AuthController.ContextItem>of()
                            : data.contexts().stream().map(ctx -> new AuthController.ContextItem(
                                    ctx.contextId(), ctx.tenantId(), ctx.userId(), ctx.actorId(),
                                    ctx.organizations() == null ? List.of()
                                            : ctx.organizations().stream()
                                                    .map(o -> new AuthController.OrgItem(o.organizationId(),
                                                            o.shortName(), o.service()))
                                                    .toList()))
                                    .toList();
                    return new AuthController.DiscoverContextsResponse(data.selectionToken(), data.expiresInSeconds(),
                            contexts);
                })
                .onErrorResume(WebClientResponseException.class, this::mapWebClientError);
    }

    @Override
    public Mono<AuthResponse> selectContext(String selectionToken, String contextId, java.util.UUID organizationId) {
        return kernelClient
                .selectContext(new KernelAuthApiClient.SelectContextRequest(selectionToken, contextId, organizationId))
                .map(resp -> {
                    if (!resp.success() || resp.data() == null) {
                        throw AuthException.invalidCredentials();
                    }
                    return mapToAuthResponse(resp.data().session());
                })
                .onErrorResume(WebClientResponseException.class, this::mapWebClientError);
    }

    // ── Actions supplémentaires exposées au AuthController ────────────────────

    public Mono<Void> forgotPassword(String email) {
        return kernelClient.forgotPassword(
                new KernelAuthApiClient.ForgotPasswordRequest(email))
                .doOnSuccess(v -> log.info("✅ [KERNEL AUTH] Forgot password envoyé : {}", email))
                .doOnError(e -> log.warn("⚠️ [KERNEL AUTH] Forgot password error: {}", e.getMessage()));
    }

    public Mono<Void> resetPassword(String resetToken, String newPassword) {
        return kernelClient.resetPassword(
                new KernelAuthApiClient.ResetPasswordRequest(resetToken, newPassword))
                .doOnSuccess(v -> log.info("✅ [KERNEL AUTH] Password réinitialisé"))
                .doOnError(e -> log.warn("⚠️ [KERNEL AUTH] Reset password error: {}", e.getMessage()));
    }

    // ── Actions non gérées par le Kernel (stubs) ──────────────────────────────

    @Override
    public Mono<UserDetail> updateUserProfile(UUID userId, String token,
            AuthUseCase.UpdateProfileCommand command) {
        log.info("🛠 [KERNEL AUTH] updateUserProfile — délégué au Kernel (non implémenté)");
        return getUserProfile(token);
    }

    @Override
    public Mono<Void> changePassword(UUID userId, String token,
            String currentPwd, String newPwd) {
        log.info("🛠 [KERNEL AUTH] changePassword — délégué au Kernel");
        return Mono.empty();
    }

    @Override
    public Mono<Void> deleteRemoteAccount(UUID userId, String token) {
        log.info("🛠 [KERNEL AUTH] deleteRemoteAccount — délégué au Kernel");
        return Mono.empty();
    }

    @Override
    public Mono<Void> moveUserToService(UUID userId, String newServiceName, String token) {
        log.info("🛠 [KERNEL AUTH] moveUserToService → {}", newServiceName);
        return Mono.empty();
    }

    @Override
    public Mono<Void> updateProfilePicture(UUID userId, String token,
            AuthUseCase.FileContent file) {
        log.info("🛠 [KERNEL AUTH] updateProfilePicture — délégué au Kernel");
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

    // ── Helpers de mapping ────────────────────────────────────────────────────

    private String ensureBearer(String token) {
        if (token == null)
            return "";
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }

    private AuthResponse mapToAuthResponse(KernelAuthApiClient.LoginResponse session) {
        if (session == null)
            throw AuthException.invalidCredentials();

        // Extraire les rôles FleetMan depuis les authorities Kernel
        List<String> roles = extractFleetRoles(session.authorities());

        UserDetail user = new UserDetail(
                session.id(),
                session.username(),
                session.email(),
                session.phoneNumber(),
                null, null,
                serviceName,
                roles,
                session.authorities() != null ? session.authorities() : List.of(),
                null, null, null, null, true,
                null);

        return new AuthResponse(
                session.accessToken(),
                session.sessionToken() != null ? session.sessionToken() : "",
                user);
    }

    private UserDetail mapToUserDetail(KernelAuthApiClient.UserAccountResponse u) {
        List<String> roles = extractFleetRoles(u.authorities());
        return new UserDetail(
                u.id(), u.username(), u.email(), u.phoneNumber(),
                null, null, serviceName,
                roles,
                u.authorities() != null ? u.authorities() : List.of(),
                null, null, null, null,
                "ACTIVE".equals(u.status()),
                null);
    }

    private UserDetail mapToUserDetailWithRoles(KernelAuthApiClient.UserAccountResponse u, List<String> roles) {
        return new UserDetail(
                u.id(), u.username(), u.email(), u.phoneNumber(),
                null, null, serviceName,
                roles,
                u.authorities() != null ? u.authorities() : List.of(),
                null, null, null, null,
                "ACTIVE".equals(u.status()),
                null);
    }

    /**
     * Extrait les rôles FleetMan depuis les authorities Kernel.
     * Les authorities Kernel sont de la forme : ROLE_FLEET_MANAGER, fleet:read,
     * etc.
     */
    private List<String> extractFleetRoles(List<String> authorities) {
        if (authorities == null)
            return List.of();
        return authorities.stream()
                .filter(a -> a.startsWith("ROLE_FLEET_") || a.startsWith("FLEET_"))
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .map(a -> a.contains("#") ? a.substring(0, a.indexOf("#")) : a)
                .distinct()
                .toList();
    }

    /**
     * Extrait les authorities (permissions/rôles) directement depuis le claim JWT.
     * Le JWT contient un claim "permissions" qui est la source fiable validée par
     * Kernel.
     */
    private List<String> extractAuthoritiesFromJwt(String token) {
        try {
            String rawToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            String[] parts = rawToken.split("\\.");
            if (parts.length < 2)
                return List.of();

            String payloadJson = new String(
                    Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode payload = objectMapper.readTree(payloadJson);
            JsonNode permissions = payload.get("permissions");

            if (permissions == null || !permissions.isArray()) {
                log.warn("⚠️ [JWT] Aucun claim 'permissions' dans le token");
                return List.of();
            }

            List<String> result = new ArrayList<>();
            permissions.forEach(n -> result.add(n.asText()));
            log.debug("🔎 [JWT CLAIMS] permissions extraites : {}", result);
            return result;
        } catch (Exception e) {
            log.error("❌ [KERNEL AUTH] Impossible de décoder les claims du JWT : {}", e.getMessage());
            return List.of();
        }
    }

    private <T> Mono<T> mapWebClientError(WebClientResponseException ex) {
        log.error("❌ [KERNEL AUTH] Erreur HTTP {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        return switch (ex.getStatusCode().value()) {
            case 401 -> Mono.error(AuthException.invalidCredentials());
            case 403 -> Mono.error(AuthException.accountLocked());
            case 404 -> Mono.error(AuthException.generic("Ressource introuvable dans le Kernel",
                    HttpStatus.NOT_FOUND));
            case 409 -> Mono.error(AuthException.userAlreadyExists());
            default -> Mono.error(AuthException.generic(
                    "Erreur Kernel [" + ex.getStatusCode() + "]: " + ex.getResponseBodyAsString(),
                    (HttpStatus) ex.getStatusCode()));
        };
    }
}
