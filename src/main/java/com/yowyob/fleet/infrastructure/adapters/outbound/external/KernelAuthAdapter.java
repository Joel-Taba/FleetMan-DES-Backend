package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.domain.exception.AuthException;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.AuthController;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelAdminApiClient;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelAuthApiClient;
import com.yowyob.fleet.infrastructure.config.KernelCallSupport;
import com.yowyob.fleet.infrastructure.config.KernelTokenHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
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
 * 1. POST /api/auth/discover-contexts  → selectionToken + contexts[]
 * 2. POST /api/auth/select-context     → JWT RS256 (accessToken)
 *
 * Sécurité : headers X-Client-Id + X-Api-Key injectés automatiquement par le WebClient.
 */
@Slf4j
public class KernelAuthAdapter implements AuthPort {

    private final KernelAuthApiClient kernelClient;
    private final KernelAdminApiClient kernelAdminClient;
    private final KernelTokenHolder kernelTokenHolder;
    private final WebClient kernelWebClient;
    private final KernelCallSupport kernelCallSupport;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KernelAuthAdapter(
            KernelAuthApiClient kernelClient,
            KernelAdminApiClient kernelAdminClient,
            KernelTokenHolder kernelTokenHolder,
            WebClient kernelWebClient,
            KernelCallSupport kernelCallSupport) {
        this.kernelClient = kernelClient;
        this.kernelAdminClient = kernelAdminClient;
        this.kernelTokenHolder = kernelTokenHolder;
        this.kernelWebClient = kernelWebClient;
        this.kernelCallSupport = kernelCallSupport;
    }

    @Value("${application.kernel.service:FLEET_MANAGEMENT}")
    private String serviceName;

    @Value("${application.kernel.organization-id:}")
    private String defaultOrgId;

    @Value("${application.kernel.tenant-id:}")
    private String defaultTenantId;

    // ── Login (2 étapes) ──────────────────────────────────────────────────────

    @Override
    public Mono<AuthResponse> login(String identifier, String password) {
        log.info("🔑 [KERNEL AUTH] Login pour : {}", identifier);

        // Étape 1 : discover-contexts — pas de circuit breaker ici : une erreur auth
        // ne doit pas être avalée (sinon réponse HTTP 200 vide côté client).
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

                    var selected = selectContextAndOrganization(discover.contexts())
                            .orElseThrow(AuthException::invalidCredentials);

                    log.debug("✅ [KERNEL AUTH] Context sélectionné: {} org={}",
                            selected.contextId(), selected.organizationId());

                    return kernelClient.selectContext(
                            new KernelAuthApiClient.SelectContextRequest(
                                    discover.selectionToken(),
                                    selected.contextId(),
                                    selected.organizationId()
                            ));
                })
                .map(resp -> {
                    if (!resp.success() || resp.data() == null) {
                        throw AuthException.invalidCredentials();
                    }
                    var session = resp.data().session();
                    return mapToAuthResponse(session);
                })
                .onErrorResume(WebClientResponseException.class, this::mapWebClientError)
                .onErrorResume(this::mapConnectivityError)
                .doOnSuccess(r -> log.info("✅ [KERNEL AUTH] Login réussi : {}", identifier));
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────

    @Override
    public Mono<AuthResponse> refresh(String refreshToken) {
        // Pas de circuit breaker ici : une réponse vide corromprait la session côté client.
        return kernelClient.refreshToken(new KernelAuthApiClient.RefreshTokenRequest(refreshToken))
                .flatMap(resp -> {
                    if (!resp.success() || resp.data() == null) {
                        return Mono.error(AuthException.tokenExpired());
                    }
                    var tokens = resp.data();
                    String accessToken = tokens.accessToken();
                    String nextRefresh = tokens.refreshToken() != null && !tokens.refreshToken().isBlank()
                            ? tokens.refreshToken()
                            : refreshToken;
                    return parseUserDetailFromJwt(accessToken)
                            .map(user -> new AuthResponse(accessToken, nextRefresh, user));
                })
                .onErrorResume(WebClientResponseException.class, this::mapWebClientError)
                .onErrorResume(this::mapConnectivityError);
    }

    // ── Inscription ───────────────────────────────────────────────────────────

    @Override
    public Mono<AuthResponse> registerInRemote(AuthUseCase.RegisterCommand command) {
        log.info("📝 [KERNEL AUTH] Inscription via owner : {}", command.email());
        return kernelCallSupport.run("kernel-auth",
                kernelTokenHolder.getValidAccessToken()
                .flatMap(ownerToken -> kernelAdminClient.registerUser(
                        ensureBearer(ownerToken),
                        defaultTenantId,
                        defaultOrgId,
                        new KernelAuthApiClient.RegisterUserRequest(
                                command.username(),
                                command.email(),
                                command.phone(),
                                command.password(),
                                "LOCAL"
                        )))
                .flatMap(resp -> {
                    if (!resp.success() || resp.data() == null) {
                        return Mono.error(AuthException.generic(resp.message(), HttpStatus.BAD_REQUEST));
                    }
                    var user = resp.data();
                    UserDetail detail = new UserDetail(
                            user.id(), user.username(), user.email(), user.phoneNumber(),
                            command.firstName(), command.lastName(), serviceName,
                            command.roles(), List.of(),
                            null, null, null, null, true, null
                    );
                    return Mono.just(new AuthResponse("", "", detail));
                })
                .onErrorResume(WebClientResponseException.class, this::mapWebClientError)
                .doOnSuccess(r -> log.info("✅ [KERNEL AUTH] Inscription réussie : {}", command.email())));
    }

    // ── Profil utilisateur ────────────────────────────────────────────────────

    /**
     * Extrait le profil utilisateur DIRECTEMENT depuis les claims JWT,
     * sans appel réseau vers le Kernel.
     *
     * Le JWT (RS256 signé par le Kernel) contient :
     * - sub        : userId (UUID Kernel)
     * - permissions: rôles et permissions (source fiable)
     * - tid        : tenantId
     * - exp        : expiration
     */
    @Override
    public Mono<UserDetail> getUserProfile(String token) {
        try {
            String rawToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            return parseUserDetailFromJwt(rawToken);
        } catch (Exception e) {
            log.error("❌ [KERNEL AUTH] Erreur lecture JWT : {}", e.getMessage());
            return Mono.error(AuthException.tokenExpired());
        }
    }

    private Mono<UserDetail> parseUserDetailFromJwt(String rawToken) {
        try {
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

            log.debug("✅ [KERNEL AUTH] Profil depuis JWT : userId={}, roles={}", userId, roles);

            return Mono.just(new UserDetail(
                    userId, sub, null, null,
                    null, null, serviceName,
                    roles, authorities,
                    null, null, null, null, true, null
            ));
        } catch (Exception e) {
            log.error("❌ [KERNEL AUTH] Erreur décodage JWT : {}", e.getMessage());
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
                    if (!resp.success() || resp.data() == null) return Flux.empty();
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
                                                    .map(o -> new AuthController.OrgItem(o.organizationId(), o.shortName(), o.service()))
                                                    .toList()
                            )).toList();
                    return new AuthController.DiscoverContextsResponse(data.selectionToken(), data.expiresInSeconds(), contexts);
                })
                .onErrorResume(WebClientResponseException.class, this::mapWebClientError);
    }

    @Override
    public Mono<AuthResponse> selectContext(String selectionToken, String contextId, java.util.UUID organizationId) {
        return kernelClient.selectContext(new KernelAuthApiClient.SelectContextRequest(selectionToken, contextId, organizationId))
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
        String bearer = ensureBearer(token);
        // 1. Vérifie réellement l'ancien mot de passe via une tentative de connexion
        //    (le JWT ne porte pas l'email, on le récupère via /api/users/me).
        return kernelClient.getMe(bearer)
                .flatMap(resp -> {
                    if (!resp.success() || resp.data() == null || resp.data().email() == null) {
                        return Mono.error(AuthException.tokenExpired());
                    }
                    return login(resp.data().email(), currentPwd);
                })
                .onErrorMap(e -> AuthException.generic(
                        "Ancien mot de passe incorrect.", HttpStatus.UNPROCESSABLE_ENTITY))
                // 2. Ancien mot de passe confirmé valide : applique le nouveau.
                .then(Mono.defer(() -> kernelClient.changePassword(
                                bearer,
                                new KernelAuthApiClient.ChangePasswordRequest(currentPwd, newPwd))
                        .onErrorResume(WebClientResponseException.class, ex -> {
                            log.error("❌ [KERNEL AUTH] changePassword indisponible côté Kernel : {}", ex.getMessage());
                            return Mono.error(AuthException.generic(
                                    "Le changement de mot de passe n'est pas encore disponible sur la plateforme "
                                            + "d'identité. Contactez un administrateur.",
                                    HttpStatus.SERVICE_UNAVAILABLE));
                        })));
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

    private record SelectedLoginContext(String contextId, UUID organizationId) {}

    /**
     * Choisit contexte + organisation depuis la réponse discover-contexts.
     * N'utilise jamais {@code defaultOrgId} : l'org doit appartenir au contexte sélectionné.
     */
    private java.util.Optional<SelectedLoginContext> selectContextAndOrganization(
            List<KernelAuthApiClient.DiscoveredContext> contexts) {
        for (var ctx : contexts) {
            if (ctx.organizations() == null) {
                continue;
            }
            for (var org : ctx.organizations()) {
                if (serviceName.equalsIgnoreCase(org.service())) {
                    return java.util.Optional.of(
                            new SelectedLoginContext(ctx.contextId(), org.organizationId()));
                }
            }
        }
        for (var ctx : contexts) {
            if (ctx.organizations() != null && !ctx.organizations().isEmpty()) {
                var org = ctx.organizations().get(0);
                return java.util.Optional.of(
                        new SelectedLoginContext(ctx.contextId(), org.organizationId()));
            }
        }
        // Contexte sans organisation listée (cas manager/chauffeur) → organizationId null
        for (var ctx : contexts) {
            if (ctx.contextId() != null && !ctx.contextId().isBlank()) {
                return java.util.Optional.of(new SelectedLoginContext(ctx.contextId(), null));
            }
        }
        return java.util.Optional.empty();
    }

    private UUID resolveOrganizationId(KernelAuthApiClient.DiscoveredContext ctx) {
        if (ctx.organizations() != null && !ctx.organizations().isEmpty()) {
            return ctx.organizations().stream()
                    .filter(o -> serviceName.equalsIgnoreCase(o.service()))
                    .map(KernelAuthApiClient.OrganizationRef::organizationId)
                    .findFirst()
                    .orElse(ctx.organizations().get(0).organizationId());
        }
        if (defaultOrgId != null && !defaultOrgId.isBlank()) {
            try {
                return UUID.fromString(defaultOrgId);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private String ensureBearer(String token) {
        if (token == null) return "";
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }

    private AuthResponse mapToAuthResponse(KernelAuthApiClient.LoginResponse session) {
        if (session == null) throw AuthException.invalidCredentials();

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
                null
        );

        // session.refreshToken() est le vrai refresh token opaque Kernel (distinct
        // de accessToken/sessionToken) : sans lui le front ne peut jamais distinguer
        // "j'ai un refresh token valide" de "je n'en ai pas", et retombe toujours
        // sur la reconnexion interactive.
        return new AuthResponse(
                session.accessToken(),
                session.refreshToken() != null ? session.refreshToken() : "",
                user
        );
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
                null
        );
    }

    private UserDetail mapToUserDetailWithRoles(KernelAuthApiClient.UserAccountResponse u, List<String> roles) {
        return new UserDetail(
                u.id(), u.username(), u.email(), u.phoneNumber(),
                null, null, serviceName,
                roles,
                u.authorities() != null ? u.authorities() : List.of(),
                null, null, null, null,
                "ACTIVE".equals(u.status()),
                null
        );
    }

    /**
     * Extrait les rôles FleetMan depuis les authorities Kernel.
     * Les authorities Kernel sont de la forme : ROLE_FLEET_MANAGER, fleet:read, etc.
     */
    private List<String> extractFleetRoles(List<String> authorities) {
        if (authorities == null) return List.of();
        return authorities.stream()
                .filter(a -> a.startsWith("ROLE_FLEET_") || a.startsWith("FLEET_"))
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .map(a -> a.contains("#") ? a.substring(0, a.indexOf("#")) : a)
                .distinct()
                .toList();
    }

    /**
     * Extrait les authorities (permissions/rôles) directement depuis le claim JWT.
     * Le JWT contient un claim "permissions" qui est la source fiable validée par Kernel.
     */
    private List<String> extractAuthoritiesFromJwt(String token) {
        try {
            String rawToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            String[] parts = rawToken.split("\\.");
            if (parts.length < 2) return List.of();

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
        String body = ex.getResponseBodyAsString();
        if (ex.getStatusCode().value() == 401 && body != null && body.contains("AUTH_INVALID_REFRESH_TOKEN")) {
            return Mono.error(AuthException.tokenExpired());
        }
        return switch (ex.getStatusCode().value()) {
            case 401 -> Mono.error(AuthException.invalidCredentials());
            case 403 -> Mono.error(AuthException.accountLocked());
            case 404 -> Mono.error(AuthException.generic("Ressource introuvable dans le Kernel",
                    HttpStatus.NOT_FOUND));
            case 409 -> Mono.error(AuthException.userAlreadyExists());
            default  -> Mono.error(AuthException.generic(
                    "Erreur Kernel [" + ex.getStatusCode() + "]: " + ex.getResponseBodyAsString(),
                    (HttpStatus) ex.getStatusCode()));
        };
    }

    private <T> Mono<T> mapConnectivityError(Throwable ex) {
        if (ex instanceof AuthException authException) {
            return Mono.error(authException);
        }
        if (ex instanceof WebClientResponseException webClientResponseException) {
            return mapWebClientError(webClientResponseException);
        }
        if (isConnectivityFailure(ex)) {
            log.error("❌ [KERNEL AUTH] Service Kernel injoignable : {}", ex.getMessage());
            return Mono.error(AuthException.remoteServiceUnavailable());
        }
        return Mono.error(ex);
    }

    private static boolean isConnectivityFailure(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof WebClientRequestException
                    || current instanceof java.net.UnknownHostException
                    || current instanceof java.net.ConnectException
                    || current instanceof java.nio.channels.ClosedChannelException
                    || current instanceof io.netty.handler.timeout.ReadTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
