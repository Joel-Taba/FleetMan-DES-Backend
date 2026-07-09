package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.SubscriptionPlan;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.in.ManageSubscriptionPlanUseCase;
import com.yowyob.fleet.domain.ports.in.ManageSuperAdminUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ApiResponse;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import com.yowyob.fleet.infrastructure.config.security.FleetPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/admin/super")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_SUPER_ADMIN)
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('FLEET_SUPER_ADMIN')")
public class SuperAdminController {

    private final ManageSuperAdminUseCase superAdminUseCase;
    private final ManageSubscriptionPlanUseCase planUseCase;
    private final com.yowyob.fleet.application.service.AppSettingsService appSettingsService;
    private final com.yowyob.fleet.application.service.SubscriptionRegistrationService registrationService;

    public record CreatePlanRequest(
        @NotBlank String name,
        String description,
        int maxFleets,
        int maxVehicles,
        int maxDrivers,
        java.math.BigDecimal monthlyPrice,
        java.math.BigDecimal annualPrice,
        String currency,
        String features,
        java.util.List<ManageSubscriptionPlanUseCase.PlanFeatureCommand> technicalFeatures
    ) {}

    public record UpdatePlanRequest(
        String name,
        String description,
        int maxFleets,
        int maxVehicles,
        int maxDrivers,
        java.math.BigDecimal monthlyPrice,
        java.math.BigDecimal annualPrice,
        String features
    ) {}

    public record AssignPlanRequest(@NotNull UUID planId) {}

    public record RejectRequest(
        @NotBlank String reason,
        String subject,
        String message
    ) {}

    public record CreateAdminRequest(
        @NotBlank String username,
        @NotBlank String password,
        @Email @NotBlank String email,
        @NotBlank String phone,
        @NotBlank String firstName,
        @NotBlank String lastName
    ) {}

    @PostMapping(
        value = "/admins",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Créer un nouvel Administrateur",
        description = "Le rôle FLEET_ADMIN est ajouté automatiquement.",
        requestBody = @RequestBody(
            content = @Content(
                mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                schema = @Schema(implementation = CreateAdminSchema.class),
                encoding = @Encoding(
                    name = "user",
                    contentType = "application/json"
                )
            )
        )
    )
    public Mono<AuthPort.AuthResponse> create(
        @RequestPart("user") @Valid CreateAdminRequest req,
        @RequestPart(value = "file", required = false) Part filePart
    ) {
        return processFilePart(filePart)
            .map(photo ->
                new AuthUseCase.RegisterCommand(
                    req.username(),
                    req.password(),
                    req.email(),
                    req.phone(),
                    req.firstName(),
                    req.lastName(),
                    List.of("FLEET_ADMIN"),
                    photo
                )
            )
            .switchIfEmpty(
                Mono.just(
                    new AuthUseCase.RegisterCommand(
                        req.username(),
                        req.password(),
                        req.email(),
                        req.phone(),
                        req.firstName(),
                        req.lastName(),
                        List.of("FLEET_ADMIN"),
                        null
                    )
                )
            )
            .flatMap(superAdminUseCase::createAdmin);
    }

    private Mono<AuthUseCase.FileContent> processFilePart(Part fp) {
        if (fp == null) return Mono.empty();
        String filename = (fp instanceof FilePart file)
            ? file.filename()
            : "admin_picture";
        return DataBufferUtils.join(fp.content()).map(dataBuffer -> {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);
            return new AuthUseCase.FileContent(
                filename,
                fp.headers().getContentType() != null
                    ? fp.headers().getContentType().toString()
                    : "image/jpeg",
                bytes
            );
        });
    }

    @GetMapping("/admins")
    @Operation(summary = "Lister les Administrateurs")
    public Flux<AuthPort.UserDetail> list(
        @Parameter(hidden = true) @RequestHeader(
            HttpHeaders.AUTHORIZATION
        ) String t
    ) {
        return superAdminUseCase.listAdmins(t);
    }

    @GetMapping("/admins/{id}")
    @Operation(summary = "Détails d'un Administrateur")
    public Mono<AuthPort.UserDetail> getOne(
        @PathVariable UUID id,
        @Parameter(hidden = true) @RequestHeader(
            HttpHeaders.AUTHORIZATION
        ) String t
    ) {
        return superAdminUseCase.getAdminDetails(id, t);
    }

    @PatchMapping("/admins/{id}/toggle")
    @Operation(summary = "Activer/Désactiver un Administrateur")
    public Mono<Void> toggle(@PathVariable UUID id, Authentication auth) {
        AuthPort.UserDetail currentUser =
            (AuthPort.UserDetail) auth.getPrincipal();
        return superAdminUseCase.toggleAdminStatus(id, currentUser.id());
    }

    // ── PLANS TARIFAIRES ─────────────────────────────────────────────────────

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un plan tarifaire")
    public Mono<SubscriptionPlan> createPlan(
        @Valid @org.springframework.web.bind.annotation.RequestBody CreatePlanRequest req
    ) {
        return planUseCase.createPlan(
            new ManageSubscriptionPlanUseCase.CreatePlanCommand(
                req.name(),
                req.description(),
                req.maxFleets(),
                req.maxVehicles(),
                req.maxDrivers(),
                req.monthlyPrice(),
                req.annualPrice(),
                req.currency(),
                req.features()
            )
        ).flatMap(plan -> {
            if (req.technicalFeatures() == null || req.technicalFeatures().isEmpty()) {
                return Mono.just(plan);
            }
            return planUseCase.replacePlanFeatures(plan.getId(), req.technicalFeatures())
                    .thenReturn(plan);
        });
    }

    @GetMapping("/plans")
    @Operation(summary = "Lister tous les plans")
    public Flux<SubscriptionPlan> listPlans() {
        return planUseCase.listPlans();
    }

    @PutMapping("/plans/{id}")
    @Operation(summary = "Modifier un plan")
    public Mono<SubscriptionPlan> updatePlan(
        @PathVariable UUID id,
        @Valid @org.springframework.web.bind.annotation.RequestBody UpdatePlanRequest req
    ) {
        return planUseCase.updatePlan(
            id,
            new ManageSubscriptionPlanUseCase.UpdatePlanCommand(
                req.name(),
                req.description(),
                req.maxFleets(),
                req.maxVehicles(),
                req.maxDrivers(),
                req.monthlyPrice(),
                req.annualPrice(),
                req.features()
            )
        );
    }

    @DeleteMapping("/plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Désactiver un plan")
    public Mono<Void> deactivatePlan(@PathVariable UUID id) {
        return planUseCase.deactivatePlan(id);
    }

    @PostMapping("/managers/{id}/plan")
    @Operation(summary = "Affecter un plan à un gestionnaire")
    public Mono<Void> assignPlan(
        @PathVariable UUID id,
        @org.springframework.web.bind.annotation.RequestBody AssignPlanRequest req
    ) {
        return planUseCase.assignPlanToManager(id, req.planId());
    }

    // ── WORKFLOW APPROBATION SOUSCRIPTIONS ───────────────────────────────────

    @GetMapping("/subscriptions/pending")
    @Operation(summary = "Lister les demandes d'inscription en attente")
    public Flux<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.PendingSubscriptionDto> listPending() {
        return planUseCase.listPendingSubscriptions();
    }

    @GetMapping("/subscriptions/history")
    @Operation(summary = "Historique des demandes approuvées ou rejetées")
    public Flux<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SubscriptionHistoryDto> listHistory() {
        return planUseCase.listSubscriptionHistory();
    }

    @PatchMapping("/subscriptions/{id}/approve")
    @Operation(summary = "Approuver une inscription de gestionnaire")
    public Mono<Void> approve(
        @PathVariable UUID id,
        @org.springframework.web.bind.annotation.RequestBody(
            required = false
        ) AssignPlanRequest req,
        Authentication auth
    ) {
        UUID currentUserId = getUserId(auth);
        UUID planId = req != null ? req.planId() : null;
        return planUseCase.approveSubscription(
            new ManageSubscriptionPlanUseCase.ApproveSubscriptionCommand(
                id,
                currentUserId,
                planId
            )
        );
    }

    @PatchMapping("/subscriptions/{id}/reject")
    @Operation(summary = "Rejeter une inscription de gestionnaire")
    @PreAuthorize("hasRole('FLEET_SUPER_ADMIN') or hasAuthority('" + FleetPermissions.SUBSCRIPTION_MANAGE + "')")
    public Mono<ApiResponse<java.util.Map<String, String>>> reject(
        @PathVariable UUID id,
        @org.springframework.web.bind.annotation.RequestBody RejectRequest req,
        Authentication auth
    ) {
        return planUseCase.rejectSubscription(
            new ManageSubscriptionPlanUseCase.RejectSubscriptionCommand(
                id,
                getUserId(auth),
                req.reason(),
                req.subject(),
                req.message()
            )
        ).thenReturn(ApiResponse.ok(java.util.Map.of("status", "REJECTED", "id", id.toString())));
    }

    @GetMapping("/subscriptions/{id}/documents")
    @Operation(summary = "Documents fournis lors de la demande de souscription")
    public Flux<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SubscriptionDocumentResponse> listSubscriptionDocuments(
        @PathVariable UUID id
    ) {
        return registrationService.listDocuments(id);
    }

    @GetMapping("/settings/subscription-grace-days")
    @Operation(summary = "Période de grâce après expiration d'abonnement")
    public Mono<java.util.Map<String, Integer>> getGraceDays() {
        return appSettingsService.getSubscriptionGraceDays()
                .map(days -> java.util.Map.of("graceDays", days));
    }

    public record GraceDaysRequest(int graceDays) {}

    @PutMapping("/settings/subscription-grace-days")
    @Operation(summary = "Modifier la période de grâce après expiration")
    public Mono<java.util.Map<String, Integer>> updateGraceDays(
        @org.springframework.web.bind.annotation.RequestBody GraceDaysRequest req
    ) {
        return appSettingsService.updateSubscriptionGraceDays(req.graceDays())
                .map(days -> java.util.Map.of("graceDays", days));
    }

    @GetMapping("/subscriptions/active")
    @Operation(summary = "Abonnements actifs et expirés")
    public Flux<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ActiveSubscriptionDto> listActiveSubscriptions() {
        return planUseCase.listActiveSubscriptions();
    }

    @GetMapping("/plans/{id}/features")
    @Operation(summary = "Fonctionnalités d'un plan")
    public Flux<ManageSubscriptionPlanUseCase.PlanFeatureCommand> getPlanFeatures(@PathVariable UUID id) {
        return planUseCase.getPlanFeatures(id);
    }

    public record PlanFeatureUpdateRequest(
        java.util.List<ManageSubscriptionPlanUseCase.PlanFeatureCommand> features
    ) {}

    @PutMapping("/plans/{id}/features")
    @Operation(summary = "Remplacer les fonctionnalités d'un plan")
    public Mono<Void> updatePlanFeatures(
        @PathVariable UUID id,
        @org.springframework.web.bind.annotation.RequestBody PlanFeatureUpdateRequest req
    ) {
        return planUseCase.replacePlanFeatures(id, req.features());
    }

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    // Helper Schema pour Swagger
    @Schema(name = "CreateAdminRequestMultipart")
    private static class CreateAdminSchema {

        @Schema(
            description = "Infos Admin (JSON)",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        public CreateAdminRequest user;

        @Schema(
            description = "Photo (Fichier)",
            type = "string",
            format = "binary"
        )
        public String file;
    }
}
