package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.PasswordResetService;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ApiResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.LoginRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.RegisterRequest;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.KernelAuthAdapter;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_AUTH)
public class AuthController {

    private final AuthUseCase authUseCase;
    private final AuthPort authPort;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    @Operation(summary = "Connexion utilisateur",
               description = "Flow Kernel 2 étapes : discover-contexts puis select-context. Retourne JWT RS256.")
    public Mono<ApiResponse<AuthPort.AuthResponse>> login(
            @org.springframework.web.bind.annotation.RequestBody LoginRequest request) {
        return authUseCase.login(request.identifier(), request.password()).map(ApiResponse::ok);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rafraîchir le token JWT")
    public Mono<ApiResponse<AuthPort.AuthResponse>> refresh(
            @org.springframework.web.bind.annotation.RequestBody TokenRefreshRequest request) {
        return authUseCase.refreshToken(request.refreshToken()).map(ApiResponse::ok);
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Inscription utilisateur",
               description = "Crée un compte via le Kernel RT-Comops. Retourne les tokens d'accès.")
    public Mono<AuthPort.AuthResponse> register(
            @org.springframework.web.bind.annotation.RequestBody RegisterRequest dto) {
        var command = new AuthUseCase.RegisterCommand(
                dto.username(), dto.password(), dto.email(), dto.phone(),
                dto.firstName(), dto.lastName(), dto.roles(), null);
        return authUseCase.register(command);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mot de passe oublié",
               description = "Envoie un email de réinitialisation (Kernel ou mode fake local).")
    public Mono<Void> forgotPassword(
            @org.springframework.web.bind.annotation.RequestBody ForgotPasswordRequest request) {
        log.info("🔑 Forgot password request for: {}", request.email());
        return passwordResetService.forgotPassword(request.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Réinitialiser le mot de passe")
    public Mono<Void> resetPassword(
            @org.springframework.web.bind.annotation.RequestBody ResetPasswordRequest request) {
        return passwordResetService.resetPassword(request.resetToken(), request.newPassword());
    }

    @GetMapping("/me")
    @Operation(summary = "Profil de l'utilisateur connecté")
    public Mono<AuthPort.UserDetail> me(
            @RequestHeader("Authorization") String token) {
        return authPort.getUserProfile(token);
    }

    @PostMapping("/discover-contexts")
    @Operation(summary = "Découvrir les contextes (multi-tenant)",
               description = "Étape 1 du flow Kernel : retourne les tenants et organisations accessibles.")
    public Mono<DiscoverContextsResponse> discoverContexts(
            @org.springframework.web.bind.annotation.RequestBody DiscoverContextsRequest request) {
        log.info("🔍 discover-contexts pour: {}", request.principal());
        if (authPort instanceof KernelAuthAdapter kernelAdapter) {
            return kernelAdapter.discoverContexts(request.principal(), request.password());
        }
        return Mono.just(new DiscoverContextsResponse(
                "fake-selection-token",
                900L,
                List.of(new ContextItem("ctx-fake", null, null, null, List.of()))
        ));
    }

    @PostMapping("/select-context")
    @Operation(summary = "Sélectionner un contexte (multi-tenant)",
               description = "Étape 2 du flow Kernel : finalise le login pour le contexte choisi.")
    public Mono<AuthPort.AuthResponse> selectContext(
            @org.springframework.web.bind.annotation.RequestBody SelectContextRequest request) {
        log.info("✅ select-context: contextId={} orgId={}", request.contextId(), request.organizationId());
        return authUseCase.selectContext(request.selectionToken(), request.contextId(), request.organizationId());
    }

    public record TokenRefreshRequest(String refreshToken) {}
    public record ForgotPasswordRequest(String email) {}
    public record ResetPasswordRequest(String resetToken, String newPassword) {}

    public record DiscoverContextsRequest(String principal, String password) {}
    public record SelectContextRequest(String selectionToken, String contextId, java.util.UUID organizationId) {}

    public record DiscoverContextsResponse(
            String selectionToken,
            Long expiresInSeconds,
            java.util.List<ContextItem> contexts) {}

    public record ContextItem(
            String contextId,
            java.util.UUID tenantId,
            java.util.UUID userId,
            java.util.UUID actorId,
            java.util.List<OrgItem> organizations) {}

    public record OrgItem(java.util.UUID organizationId, String shortName, String service) {}
}
