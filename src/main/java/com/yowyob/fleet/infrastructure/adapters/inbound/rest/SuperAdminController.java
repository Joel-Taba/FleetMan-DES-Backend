package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.SubscriptionPlan;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.in.ManageSubscriptionPlanUseCase;
import com.yowyob.fleet.domain.ports.in.ManageSuperAdminUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
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

    public record CreatePlanRequest(
        @NotBlank String name,
        String description,
        int maxFleets,
        int maxVehicles,
        int maxDrivers,
        java.math.BigDecimal monthlyPrice,
        java.math.BigDecimal annualPrice,
        String currency,
        String features
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

    public record RejectRequest(@NotBlank String reason) {}

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
        @Valid @RequestBody CreatePlanRequest req
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
        );
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
        @Valid @RequestBody UpdatePlanRequest req
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
        @RequestBody AssignPlanRequest req
    ) {
        return planUseCase.assignPlanToManager(id, req.planId());
    }

    // ── WORKFLOW APPROBATION SOUSCRIPTIONS ───────────────────────────────────

    @GetMapping("/subscriptions/pending")
    @Operation(summary = "Lister les demandes d'inscription en attente")
    public Flux<Object> listPending() {
        return planUseCase.listPendingSubscriptions();
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
    public Mono<Void> reject(
        @PathVariable UUID id,
        @org.springframework.web.bind.annotation.RequestBody RejectRequest req,
        Authentication auth
    ) {
        return planUseCase.rejectSubscription(
            new ManageSubscriptionPlanUseCase.RejectSubscriptionCommand(
                id,
                getUserId(auth),
                req.reason()
            )
        );
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
