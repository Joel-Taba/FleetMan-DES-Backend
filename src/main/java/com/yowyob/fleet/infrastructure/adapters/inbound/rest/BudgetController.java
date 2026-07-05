package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.Budget;
import com.yowyob.fleet.domain.ports.in.ManageBudgetUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.BudgetRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.BudgetResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.BudgetUpdateRequest;
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

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budget/budgets")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_BUDGET_BUDGETS)
@SecurityRequirement(name = "bearerAuth")
public class BudgetController {

    private final ManageBudgetUseCase budgetUseCase;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    // ── CRÉATION ─────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Créer un budget mensuel",
               description = "Crée un budget mensuel pour une flotte (scope=FLEET) ou un véhicule (scope=VEHICLE). " +
                             "Un seul budget par entité par mois est autorisé. " +
                             "Le montant consommé est calculé automatiquement depuis les dépenses existantes.")
    public Mono<BudgetResponse> create(
            @Valid @RequestBody BudgetRequest request,
            Authentication auth) {

        ManageBudgetUseCase.CreateBudgetCommand cmd =
                new ManageBudgetUseCase.CreateBudgetCommand(
                        request.scope(),
                        request.entityId(),
                        getUserId(auth),
                        request.budgetMonth(),
                        request.amount(),
                        request.notes()
                );
        return budgetUseCase.createBudget(cmd).map(BudgetResponse::from);
    }

    // ── LECTURE ───────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Lister mes budgets",
               description = "Retourne tous les budgets créés par le manager connecté, triés par mois décroissant.")
    public Flux<BudgetResponse> listAll(Authentication auth) {
        return budgetUseCase.getAllByManager(getUserId(auth)).map(BudgetResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Détail d'un budget",
               description = "Retourne le détail complet du budget avec taux de consommation, montant restant et niveau d'alerte.")
    public Mono<BudgetResponse> getById(
            @Parameter(description = "ID du budget") @PathVariable UUID id) {
        return budgetUseCase.getById(id).map(BudgetResponse::from);
    }

    @GetMapping("/fleet/{fleetId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Budgets d'une flotte",
               description = "Retourne l'historique des budgets mensuels pour une flotte donnée.")
    public Flux<BudgetResponse> getByFleet(
            @Parameter(description = "ID de la flotte") @PathVariable UUID fleetId) {
        return budgetUseCase.getByFleet(fleetId).map(BudgetResponse::from);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Budgets d'un véhicule")
    public Flux<BudgetResponse> getByVehicle(
            @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId) {
        return budgetUseCase.getByVehicle(vehicleId).map(BudgetResponse::from);
    }

    @GetMapping("/current/fleet/{fleetId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Budget actuel d'une flotte",
               description = "Retourne le budget du mois en cours pour une flotte. Retourne 404 si aucun budget n'existe pour ce mois.")
    public Mono<BudgetResponse> getCurrentByFleet(
            @Parameter(description = "ID de la flotte") @PathVariable UUID fleetId) {
        return budgetUseCase.getCurrentBudget(Budget.BudgetScope.FLEET, fleetId)
                .map(BudgetResponse::from);
    }

    @GetMapping("/current/vehicle/{vehicleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Budget actuel d'un véhicule")
    public Mono<BudgetResponse> getCurrentByVehicle(
            @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId) {
        return budgetUseCase.getCurrentBudget(Budget.BudgetScope.VEHICLE, vehicleId)
                .map(BudgetResponse::from);
    }

    @GetMapping("/month")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Budget d'une entité pour un mois donné",
               description = "Récupère le budget d'une flotte ou véhicule pour un mois spécifique.")
    public Mono<BudgetResponse> getBudgetForMonth(
            @Parameter(description = "Portée (FLEET ou VEHICLE)", example = "FLEET")
            @RequestParam Budget.BudgetScope scope,
            @Parameter(description = "ID de l'entité (flotte ou véhicule)")
            @RequestParam UUID entityId,
            @Parameter(description = "Mois (format YYYY-MM-DD)", example = "2026-06-01")
            @RequestParam LocalDate month) {
        return budgetUseCase.getBudgetForMonth(scope, entityId, month).map(BudgetResponse::from);
    }

    // ── STATUS (résumé enrichi) ────────────────────────────────────────────────

    @GetMapping("/{id}/status")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Statut détaillé d'un budget",
               description = "Retourne le statut enrichi avec taux de consommation, montant restant et indicateur de dépassement.")
    public Mono<ManageBudgetUseCase.BudgetStatusDto> getStatus(
            @Parameter(description = "ID du budget") @PathVariable UUID id) {
        return budgetUseCase.getById(id)
                .map(ManageBudgetUseCase.BudgetStatusDto::from);
    }

    // ── RECALCUL ──────────────────────────────────────────────────────────────

    @PostMapping("/{id}/recalculate")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Recalculer le budget",
               description = "Force le recalcul du montant consommé depuis les dépenses approuvées. " +
                             "Utile après une correction manuelle ou une synchronisation.")
    public Mono<BudgetResponse> recalculate(
            @Parameter(description = "ID du budget") @PathVariable UUID id) {
        return budgetUseCase.recalculateConsumed(id).map(BudgetResponse::from);
    }

    // ── MISE À JOUR ───────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Mettre à jour un budget",
               description = "Met à jour le montant plafond ou les notes d'un budget existant.")
    public Mono<BudgetResponse> update(
            @Parameter(description = "ID du budget") @PathVariable UUID id,
            @Valid @RequestBody BudgetUpdateRequest request) {

        ManageBudgetUseCase.UpdateBudgetCommand cmd =
                new ManageBudgetUseCase.UpdateBudgetCommand(
                        id,
                        request.amount(),
                        request.notes()
                );
        return budgetUseCase.update(cmd).map(BudgetResponse::from);
    }

    // ── SUPPRESSION ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Supprimer un budget")
    public Mono<Void> delete(
            @Parameter(description = "ID du budget") @PathVariable UUID id) {
        return budgetUseCase.delete(id);
    }
}
