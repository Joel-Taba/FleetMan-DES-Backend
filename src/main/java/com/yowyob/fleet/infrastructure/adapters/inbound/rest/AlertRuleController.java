package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.in.ManageAlertRuleUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.AlertRuleRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.AlertRuleResponse;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts/rules")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_ALERT_RULES)
@SecurityRequirement(name = "bearerAuth")
public class AlertRuleController {

    private final ManageAlertRuleUseCase useCase;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Créer une règle d'alerte personnalisée",
               description = "Crée une règle personnalisée en plus des 8 règles système provisionnées automatiquement.")
    public Mono<AlertRuleResponse> create(
            @Valid @RequestBody AlertRuleRequest request,
            Authentication auth) {

        ManageAlertRuleUseCase.CreateRuleCommand cmd =
                new ManageAlertRuleUseCase.CreateRuleCommand(
                        request.name(), request.description(),
                        getUserId(auth),
                        request.triggerType(), request.actionType(),
                        request.targetRole(), request.conditionValue()
                );
        return useCase.createRule(cmd).map(AlertRuleResponse::from);
    }

    @GetMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Lister mes règles d'alerte",
               description = "Retourne toutes les règles du manager (8 règles système + règles personnalisées). Les règles système sont indiquées par systemTemplate = true.")
    public Flux<AlertRuleResponse> listAll(Authentication auth) {
        return useCase.getRulesByManager(getUserId(auth)).map(AlertRuleResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Détail d'une règle")
    public Mono<AlertRuleResponse> getById(
            @Parameter(description = "ID de la règle") @PathVariable UUID id) {
        return useCase.getRuleById(id).map(AlertRuleResponse::from);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Mettre à jour une règle",
               description = "Met à jour le nom, la description, la valeur de condition ou le canal d'une règle. Applicable aux règles système et personnalisées.")
    public Mono<AlertRuleResponse> update(
            @Parameter(description = "ID de la règle") @PathVariable UUID id,
            @RequestBody AlertRuleRequest request) {

        ManageAlertRuleUseCase.UpdateRuleCommand cmd =
                new ManageAlertRuleUseCase.UpdateRuleCommand(
                        id, request.name(), request.description(),
                        request.actionType(), request.targetRole(),
                        request.conditionValue(), null
                );
        return useCase.updateRule(cmd).map(AlertRuleResponse::from);
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Activer / désactiver une règle",
               description = "Active ou désactive une règle sans la supprimer.")
    public Mono<AlertRuleResponse> toggle(
            @Parameter(description = "ID de la règle") @PathVariable UUID id,
            @Parameter(description = "true = activer, false = désactiver")
            @RequestParam boolean active) {
        return useCase.toggleRule(id, active).map(AlertRuleResponse::from);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Supprimer une règle personnalisée",
               description = "Supprime une règle personnalisée. Les règles système (systemTemplate = true) ne peuvent pas être supprimées.")
    public Mono<Void> delete(
            @Parameter(description = "ID de la règle") @PathVariable UUID id) {
        return useCase.deleteRule(id);
    }

    @PostMapping("/provision-defaults")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Provisionner les règles par défaut",
               description = "Crée les 8 règles système par défaut pour le manager connecté. Opération idempotente : n'ajoute que les règles manquantes.")
    public Mono<Void> provisionDefaults(Authentication auth) {
        return useCase.provisionDefaultRules(getUserId(auth));
    }
}
