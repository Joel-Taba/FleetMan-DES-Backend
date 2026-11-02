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
    private final com.yowyob.fleet.application.service.ResourceAccessService resourceAccess;
    private final com.yowyob.fleet.application.service.CompanyScopeService companyScope;
    private final com.yowyob.fleet.application.service.BudgetTrackingService tracking;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    // ── SUIVI ────────────────────────────────────────────────────────────────

    @GetMapping("/tracking")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Suivi consolidé d'un mois",
               description = "Utilisation des budgets par niveau (périmètre, flotte, véhicule), par type, chauffeur et origine "
                       + "de dépense, tendance sur 6 mois, consommé jour par jour, projection de fin de mois, coût au kilomètre.")
    public Mono<com.yowyob.fleet.domain.model.BudgetTracking.Overview> tracking(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate month,
            @RequestParam(required = false) UUID fleetId,
            Authentication auth) {
        return tracking.overview(getUserId(auth), month, fleetId);
    }

    @GetMapping("/{id}/ledger")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Grand livre d'un budget",
               description = "Chaque mouvement avec le solde après coup : création, changements de montant, seuils, dépenses "
                       + "approuvées (auteur, validateur, véhicule, chauffeur, justificatif), plus les dépenses en attente et rejetées.")
    public Mono<com.yowyob.fleet.application.service.BudgetTrackingService.Ledger> ledger(
            @PathVariable UUID id, Authentication auth) {
        return tracking.ledger(getUserId(auth), id);
    }

    // ── CRÉATION ─────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Créer un budget mensuel",
               description = "Enveloppe de flotte (scope=FLEET) : administrateur uniquement. Budget de véhicule (scope=VEHICLE) : " +
                             "gestionnaire ou administrateur, dans la limite de l'enveloppe de la flotte. Mois écoulés clos. " +
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
        // La cible doit exister dans le périmètre et correspondre à la portée (flotte ou
        // véhicule) : un budget pouvait viser n'importe quel identifiant.
        return resourceAccess.assertBudgetTarget(getUserId(auth), request.scope().name(), request.entityId())
                .then(budgetUseCase.createBudget(cmd)).map(BudgetResponse::from);
    }

    // ── LECTURE ───────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Lister mes budgets",
               description = "Retourne tous les budgets créés par le manager connecté, triés par mois décroissant.")
    public Flux<BudgetResponse> listAll(Authentication auth) {
        return budgetUseCase.getAllByManager(getUserId(auth)).map(BudgetResponse::from);
    }

    @GetMapping("/company")
    @PreAuthorize("hasRole('FLEET_ADMIN')")
    @Operation(summary = "Budgets de l'entreprise pour un mois",
               description = "Enveloppes des flottes et budgets des véhicules de toute l'entreprise (mois en cours par défaut).")
    public Flux<BudgetResponse> listCompany(
            @RequestParam(required = false) LocalDate month,
            Authentication auth) {
        return companyScope.requireOwnCompany(getUserId(auth))
                .flatMapMany(companyId -> budgetUseCase.getAllByCompany(companyId, month))
                .map(BudgetResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Détail d'un budget",
               description = "Retourne le détail complet du budget avec taux de consommation, montant restant et niveau d'alerte.")
    public Mono<BudgetResponse> getById(
            @Parameter(description = "ID du budget") @PathVariable UUID id,
            Authentication auth) {
        return resourceAccess.assertBudgetAccess(getUserId(auth), id)
                .then(budgetUseCase.getById(id).map(BudgetResponse::from));
    }

    @GetMapping("/fleet/{fleetId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Budgets d'une flotte",
               description = "Retourne l'historique des budgets mensuels pour une flotte donnée.")
    public Flux<BudgetResponse> getByFleet(
            @Parameter(description = "ID de la flotte") @PathVariable UUID fleetId,
            Authentication auth) {
        return resourceAccess.assertFleetAccess(getUserId(auth), fleetId)
                .thenMany(budgetUseCase.getByFleet(fleetId).map(BudgetResponse::from));
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Budgets d'un véhicule")
    public Flux<BudgetResponse> getByVehicle(
            @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId,
            Authentication auth) {
        return resourceAccess.assertVehicleAccess(getUserId(auth), vehicleId)
                .thenMany(budgetUseCase.getByVehicle(vehicleId).map(BudgetResponse::from));
    }

    @GetMapping("/current/fleet/{fleetId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Budget actuel d'une flotte",
               description = "Retourne le budget du mois en cours pour une flotte. Retourne 404 si aucun budget n'existe pour ce mois.")
    public Mono<BudgetResponse> getCurrentByFleet(
            @Parameter(description = "ID de la flotte") @PathVariable UUID fleetId,
            Authentication auth) {
        return resourceAccess.assertFleetAccess(getUserId(auth), fleetId)
                .then(budgetUseCase.getCurrentBudget(Budget.BudgetScope.FLEET, fleetId)
                .map(BudgetResponse::from));
    }

    @GetMapping("/current/vehicle/{vehicleId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Budget actuel d'un véhicule")
    public Mono<BudgetResponse> getCurrentByVehicle(
            @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId,
            Authentication auth) {
        return resourceAccess.assertVehicleAccess(getUserId(auth), vehicleId)
                .then(budgetUseCase.getCurrentBudget(Budget.BudgetScope.VEHICLE, vehicleId)
                .map(BudgetResponse::from));
    }

    @GetMapping("/month")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Budget d'une entité pour un mois donné",
               description = "Récupère le budget d'une flotte ou véhicule pour un mois spécifique.")
    public Mono<BudgetResponse> getBudgetForMonth(
            @Parameter(description = "Portée (FLEET ou VEHICLE)", example = "FLEET")
            @RequestParam Budget.BudgetScope scope,
            @Parameter(description = "ID de l'entité (flotte ou véhicule)")
            @RequestParam UUID entityId,
            @Parameter(description = "Mois (format YYYY-MM-DD)", example = "2026-06-01")
            @RequestParam LocalDate month,
            Authentication auth) {
        return resourceAccess.assertBudgetTarget(getUserId(auth), scope.name(), entityId)
                .then(budgetUseCase.getBudgetForMonth(scope, entityId, month)).map(BudgetResponse::from);
    }

    // ── STATUS (résumé enrichi) ────────────────────────────────────────────────

    @GetMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Statut détaillé d'un budget",
               description = "Retourne le statut enrichi avec taux de consommation, montant restant et indicateur de dépassement.")
    public Mono<ManageBudgetUseCase.BudgetStatusDto> getStatus(
            @Parameter(description = "ID du budget") @PathVariable UUID id,
            Authentication auth) {
        return resourceAccess.assertBudgetAccess(getUserId(auth), id)
                .then(budgetUseCase.getById(id)
                .map(ManageBudgetUseCase.BudgetStatusDto::from));
    }

    // ── RECALCUL ──────────────────────────────────────────────────────────────

    @PostMapping("/{id}/recalculate")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Recalculer le budget",
               description = "Force le recalcul du montant consommé depuis les dépenses approuvées. " +
                             "Utile après une correction manuelle ou une synchronisation.")
    public Mono<BudgetResponse> recalculate(
            @Parameter(description = "ID du budget") @PathVariable UUID id,
            Authentication auth) {
        return resourceAccess.assertBudgetAccess(getUserId(auth), id)
                .then(budgetUseCase.recalculateConsumed(id).map(BudgetResponse::from));
    }

    // ── MISE À JOUR ───────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Mettre à jour un budget",
               description = "Met à jour le montant plafond ou les notes d'un budget existant.")
    public Mono<BudgetResponse> update(
            @Parameter(description = "ID du budget") @PathVariable UUID id,
            @Valid @RequestBody BudgetUpdateRequest request,
            Authentication auth) {

        ManageBudgetUseCase.UpdateBudgetCommand cmd =
                new ManageBudgetUseCase.UpdateBudgetCommand(
                        id,
                        request.amount(),
                        request.notes()
                );
        return resourceAccess.assertBudgetAccess(getUserId(auth), id)
                .then(budgetUseCase.update(cmd, getUserId(auth))).map(BudgetResponse::from);
    }

    // ── SUPPRESSION ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Supprimer un budget")
    public Mono<Void> delete(
            @Parameter(description = "ID du budget") @PathVariable UUID id,
            Authentication auth) {
        return resourceAccess.assertBudgetAccess(getUserId(auth), id)
                .then(budgetUseCase.delete(id, getUserId(auth)));
    }
}
