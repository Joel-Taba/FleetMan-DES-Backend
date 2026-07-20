package com.yowyob.fleet.infrastructure.adapters.outbound.external.client;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Client HTTP déclaratif vers le Kernel RT-Comops.
 * URL de base : http://kernel-core.yowyob.com
 *
 * Flow d'authentification Kernel :
 * 1. POST /api/auth/discover-contexts  → selectionToken + contexts[]
 * 2. POST /api/auth/select-context     → accessToken (JWT RS256)
 * 3. GET  /api/users/me               → profil utilisateur
 *
 * Sécurité server-to-server : X-Client-Id + X-Api-Key (injectés par le WebClient)
 */
@HttpExchange
public interface KernelAuthApiClient {

    // ── Authentification ──────────────────────────────────────────────────────

    /** Étape 1 : identifie l'utilisateur et retourne les contextes disponibles */
    @PostExchange("/api/auth/discover-contexts")
    Mono<ApiResponse<DiscoverContextsResponse>> discoverContexts(
            @RequestBody LoginRequest request);

    /** Étape 2 : sélectionne un contexte et retourne le JWT */
    @PostExchange("/api/auth/select-context")
    Mono<ApiResponse<ContextualLoginResponse>> selectContext(
            @RequestBody SelectContextRequest request);

    /** Renouvellement du JWT via refresh token */
    @PostExchange("/api/auth/refresh")
    Mono<ApiResponse<RefreshTokenResponse>> refreshToken(
            @RequestBody RefreshTokenRequest request);

    /** Inscription d'un nouvel utilisateur */
    @PostExchange("/api/auth/register")
    Mono<ApiResponse<UserAccountResponse>> register(
            @RequestBody RegisterUserRequest request);

    /** Mot de passe oublié */
    @PostExchange("/api/auth/forgot-password")
    Mono<Void> forgotPassword(@RequestBody ForgotPasswordRequest request);

    /** Réinitialisation du mot de passe */
    @PostExchange("/api/auth/reset-password")
    Mono<Void> resetPassword(@RequestBody ResetPasswordRequest request);

    /** Changement de mot de passe (utilisateur authentifié, ancien mot de passe requis) */
    @PostExchange("/api/v1/portal/change-password")
    Mono<Void> changePassword(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody ChangePasswordRequest request);

    // ── Profil utilisateur ────────────────────────────────────────────────────

    /** Profil de l'utilisateur connecté */
    @GetExchange("/api/users/me")
    Mono<ApiResponse<UserAccountResponse>> getMe(
            @RequestHeader("Authorization") String bearerToken);

    // ── Administration ─────────────────────────────────────────────────────────

    /** Liste des administrateurs */
    @GetExchange("/api/users/admins")
    Mono<ApiResponse<List<UserAccountResponse>>> listAdmins(
            @RequestHeader("Authorization") String bearerToken);

    // ── DTO Records ────────────────────────────────────────────────────────────

    /** Enveloppe générique des réponses Kernel */
    record ApiResponse<T>(
            boolean success,
            T data,
            String message,
            String errorCode,
            String timestamp
    ) {}

    record LoginRequest(String principal, String password) {}

    record SelectContextRequest(
            String selectionToken,
            String contextId,
            UUID organizationId
    ) {}

    record RefreshTokenRequest(String refreshToken) {}

    record ForgotPasswordRequest(String email) {}

    record ResetPasswordRequest(
            String resetToken,
            String newPassword
    ) {}

    record ChangePasswordRequest(
            String currentPassword,
            String newPassword
    ) {}

    record RegisterUserRequest(
            String username,
            String email,
            String phoneNumber,
            String password,
            String authProvider
    ) {}

    record DiscoverContextsResponse(
            String selectionToken,
            Long expiresInSeconds,
            List<DiscoveredContext> contexts
    ) {}

    record DiscoveredContext(
            String contextId,
            UUID tenantId,
            UUID userId,
            UUID actorId,
            List<OrganizationRef> organizations
    ) {}

    record OrganizationRef(
            UUID organizationId,
            String shortName,
            String service
    ) {}

    record SelectContextRequest2(
            String selectionToken,
            String contextId
    ) {}

    record ContextualLoginResponse(
            UUID selectedTenantId,
            UUID selectedOrganizationId,
            LoginResponse session
    ) {}

    record LoginResponse(
            UUID id,
            UUID tenantId,
            UUID actorId,
            String username,
            String email,
            String phoneNumber,
            String accessToken,
            String sessionToken,
            // Vrai refresh token opaque Kernel (préfixe "rt_", ~14j de validité),
            // distinct de accessToken/sessionToken — absent de ce record jusqu'ici,
            // Jackson l'ignorait silencieusement et FleetMan retombait sur
            // sessionToken (== accessToken) en guise de "refresh token" côté client,
            // rendant tout rafraîchissement silencieux impossible.
            String refreshToken,
            String tokenType,
            Long expiresInSeconds,
            List<String> authorities,
            List<OrganizationRef> organizations
    ) {}

    record RefreshTokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            Long accessExpiresInSeconds
    ) {}

    record UserAccountResponse(
            UUID id,
            UUID tenantId,
            UUID actorId,
            String username,
            String email,
            String phoneNumber,
            String status,
            String plan,
            List<String> authorities
    ) {}
}
