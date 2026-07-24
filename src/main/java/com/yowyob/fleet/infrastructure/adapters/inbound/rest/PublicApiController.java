package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.SubscriptionPlanService;
import com.yowyob.fleet.application.service.SubscriptionRegistrationService;
import com.yowyob.fleet.domain.model.SubscriptionPlan;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ApiResponse;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_AUTH)
public class PublicApiController {

    private final SubscriptionPlanService planService;
    private final SubscriptionRegistrationService registrationService;

    @GetMapping("/subscription-plans")
    @Operation(summary = "Plans tarifaires actifs (page d'accueil)")
    public Mono<ApiResponse<java.util.List<SubscriptionPlan>>> listActivePlans() {
        return planService.listPlans()
                .filter(SubscriptionPlan::isActive)
                .collectList()
                .map(ApiResponse::ok);
    }

    public record RegisterDocumentRequest(
            @NotBlank String docType,
            String docNumber,
            @NotBlank String fileUrl,
            String fileMimeType,
            String fileOriginalName,
            String issuer,
            String issueDate,
            String expiryDate,
            String notes
    ) {}

    public record RegisterManagerRequest(
            @NotBlank String username,
            @NotBlank
            @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,}$",
                message = "Le mot de passe doit contenir au moins 10 caractères, une majuscule, une minuscule, un chiffre et un symbole."
            )
            String password,
            @Email @NotBlank String email,
            @NotBlank String phone,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String companyName,
            UUID requestedPlanId,
            @NotEmpty @Size(max = 10) List<@Valid RegisterDocumentRequest> documents
    ) {}

    @PostMapping("/register-manager")
    @Operation(summary = "Demande d'inscription gestionnaire avec documents")
    public Mono<ApiResponse<java.util.Map<String, Object>>> registerManager(
            @Valid @RequestBody RegisterManagerRequest req
    ) {
        var docs = req.documents().stream()
                .map(d -> new SubscriptionRegistrationService.DocumentInput(
                        d.docType(), d.docNumber(), d.fileUrl(), d.fileMimeType(),
                        d.fileOriginalName(), d.issuer(), d.issueDate(), d.expiryDate(), d.notes()))
                .toList();
        return registrationService.registerManager(
                new SubscriptionRegistrationService.RegisterManagerCommand(
                        req.username(), req.password(), req.email(), req.phone(),
                        req.firstName(), req.lastName(), req.companyName(),
                        req.requestedPlanId(), docs))
                .map(ApiResponse::ok);
    }
}
