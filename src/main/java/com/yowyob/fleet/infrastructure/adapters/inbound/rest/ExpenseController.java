package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.Expense;
import com.yowyob.fleet.domain.ports.in.ManageExpenseUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.*;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import com.yowyob.fleet.infrastructure.shared.util.CsvExportUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budget/expenses")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_BUDGET_EXPENSES)
@SecurityRequirement(name = "bearerAuth")
public class ExpenseController {

        private final ManageExpenseUseCase expenseUseCase;

        private UUID getUserId(Authentication auth) {
                return ((AuthPort.UserDetail) auth.getPrincipal()).id();
        }

        // ── CRÉATION ─────────────────────────────────────────────────────────────

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER')")
        @Operation(summary = "Créer une dépense manuelle", description = "Enregistre une dépense manuelle (amende, péage, autre). La dépense démarre en statut PENDING et nécessite validation du Manager. Les dépenses FUEL, MAINTENANCE, INCIDENT sont générées automatiquement par le système.")
        public Mono<ExpenseResponse> create(
                        @Valid @RequestBody ExpenseRequest request) {

                ManageExpenseUseCase.CreateManualExpenseCommand cmd = new ManageExpenseUseCase.CreateManualExpenseCommand(
                                request.expenseType(),
                                request.amount(),
                                request.description(),
                                request.expenseDate(),
                                request.vehicleId(),
                                request.driverId());
                return expenseUseCase.createManualExpense(cmd).map(ExpenseResponse::from);
        }

        // ── LECTURE ───────────────────────────────────────────────────────────────

        @GetMapping
        @PreAuthorize("hasRole('FLEET_MANAGER')")
        @Operation(summary = "Lister mes dépenses", description = "Retourne toutes les dépenses (auto et manuelles) de toutes les flottes gérées par le manager connecté.")
        public Flux<ExpenseResponse> listAll(Authentication auth) {
                return expenseUseCase.getAllByManager(getUserId(auth)).map(ExpenseResponse::from);
        }

        @GetMapping("/{id}")
        @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER')")
        @Operation(summary = "Détail d'une dépense")
        public Mono<ExpenseResponse> getById(
                        @Parameter(description = "ID de la dépense") @PathVariable UUID id) {
                return expenseUseCase.getById(id).map(ExpenseResponse::from);
        }

        @GetMapping("/fleet/{fleetId}")
        @PreAuthorize("hasRole('FLEET_MANAGER')")
        @Operation(summary = "Dépenses d'une flotte", description = "Retourne toutes les dépenses d'une flotte spécifique.")
        public Flux<ExpenseResponse> getByFleet(
                        @Parameter(description = "ID de la flotte") @PathVariable UUID fleetId) {
                return expenseUseCase.getByFleet(fleetId).map(ExpenseResponse::from);
        }

        @GetMapping("/vehicle/{vehicleId}")
        @PreAuthorize("hasRole('FLEET_MANAGER')")
        @Operation(summary = "Dépenses d'un véhicule", description = "Retourne l'historique complet des dépenses pour un véhicule donné.")
        public Flux<ExpenseResponse> getByVehicle(
                        @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId) {
                return expenseUseCase.getByVehicle(vehicleId).map(ExpenseResponse::from);
        }

        @GetMapping("/driver/{driverId}")
        @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER')")
        @Operation(summary = "Dépenses d'un chauffeur")
        public Flux<ExpenseResponse> getByDriver(
                        @Parameter(description = "ID du chauffeur") @PathVariable UUID driverId) {
                return expenseUseCase.getByDriver(driverId).map(ExpenseResponse::from);
        }

        @GetMapping("/pending")
        @PreAuthorize("hasRole('FLEET_MANAGER')")
        @Operation(summary = "Dépenses en attente de validation", description = "Retourne uniquement les dépenses manuelles au statut PENDING. Utile pour le tableau de validation.")
        public Flux<ExpenseResponse> getPending(Authentication auth) {
                return expenseUseCase.getPendingExpenses(getUserId(auth)).map(ExpenseResponse::from);
        }

        @GetMapping("/filter")
        @PreAuthorize("hasRole('FLEET_MANAGER')")
        @Operation(summary = "Filtrer les dépenses", description = "Filtre par type ou statut. Fournir un seul paramètre à la fois.")
        public Flux<ExpenseResponse> filter(
                        @Parameter(description = "Type de dépense", example = "FUEL") @RequestParam(required = false) Expense.ExpenseType type,
                        @Parameter(description = "Statut de validation", example = "APPROVED") @RequestParam(required = false) Expense.ExpenseStatus status,
                        Authentication auth) {

                UUID managerId = getUserId(auth);
                if (type != null)
                        return expenseUseCase.getByType(type, managerId).map(ExpenseResponse::from);
                if (status != null)
                        return expenseUseCase.getByStatus(status, managerId).map(ExpenseResponse::from);
                return expenseUseCase.getAllByManager(managerId).map(ExpenseResponse::from);
        }

        @GetMapping("/range")
        @PreAuthorize("hasRole('FLEET_MANAGER')")
        @Operation(summary = "Dépenses par plage de dates")
        public Flux<ExpenseResponse> getByDateRange(
                        @Parameter(description = "Date de début (ISO 8601)", example = "2026-01-01T00:00:00") @RequestParam LocalDateTime start,
                        @Parameter(description = "Date de fin (ISO 8601)", example = "2026-12-31T23:59:59") @RequestParam LocalDateTime end,
                        Authentication auth) {
                return expenseUseCase.getByDateRange(start, end, getUserId(auth)).map(ExpenseResponse::from);
        }

        // ── AGRÉGATS / KPIs ───────────────────────────────────────────────────────

        @GetMapping("/summary")
        @PreAuthorize("hasRole('FLEET_MANAGER')")
        @Operation(summary = "Résumé des dépenses par type", description = "Retourne le total des dépenses approuvées par type (FUEL, MAINTENANCE, INCIDENT, FINE, TOLL, OTHER). Utilisé pour les graphiques camembert du dashboard.")
        public Mono<ManageExpenseUseCase.ExpenseSummaryDto> getSummary(Authentication auth) {
                return expenseUseCase.getSummaryByManager(getUserId(auth));
        }

        @GetMapping("/vehicle/{vehicleId}/total")
        @PreAuthorize("hasRole('FLEET_MANAGER')")
        @Operation(summary = "Coût total approuvé d'un véhicule")
        public Mono<TotalDto> getTotalByVehicle(
                        @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId) {
                return expenseUseCase.getTotalApprovedByVehicle(vehicleId)
                                .map(total -> new TotalDto(vehicleId, total));
        }

        @GetMapping("/fleet/{fleetId}/total")
        @PreAuthorize("hasRole('FLEET_MANAGER')")
        @Operation(summary = "Coût total approuvé d'une flotte")
        public Mono<TotalDto> getTotalByFleet(
                        @Parameter(description = "ID de la flotte") @PathVariable UUID fleetId) {
                return expenseUseCase.getTotalApprovedByFleet(fleetId)
                                .map(total -> new TotalDto(fleetId, total));
        }

        // ── VALIDATION ────────────────────────────────────────────────────────────

        @PatchMapping("/{id}/approve")
        @PreAuthorize("hasRole('FLEET_MANAGER')")
        @Operation(summary = "Approuver une dépense", description = "Approuve une dépense manuelle en attente (PENDING → APPROVED). Déclenche la mise à jour du budget correspondant.")
        public Mono<ExpenseResponse> approve(
                        @Parameter(description = "ID de la dépense") @PathVariable UUID id,
                        Authentication auth) {
                return expenseUseCase.approve(id, getUserId(auth)).map(ExpenseResponse::from);
        }

        @PatchMapping("/{id}/reject")
        @PreAuthorize("hasRole('FLEET_MANAGER')")
        @Operation(summary = "Rejeter une dépense", description = "Rejette une dépense manuelle en attente avec un motif obligatoire (PENDING → REJECTED).")
        public Mono<ExpenseResponse> reject(
                        @Parameter(description = "ID de la dépense") @PathVariable UUID id,
                        @Valid @RequestBody ExpenseRejectRequest request,
                        Authentication auth) {

                ManageExpenseUseCase.ValidateExpenseCommand cmd = new ManageExpenseUseCase.ValidateExpenseCommand(
                                id,
                                getUserId(auth),
                                request.rejectionReason());
                return expenseUseCase.reject(cmd).map(ExpenseResponse::from);
        }

        // ── MISE À JOUR ───────────────────────────────────────────────────────────

        @PutMapping("/{id}")
        @PreAuthorize("hasRole('FLEET_MANAGER')")
        @Operation(summary = "Mettre à jour une dépense manuelle", description = "Met à jour le montant ou la description d'une dépense manuelle encore en attente (PENDING). Interdit sur les dépenses auto-générées ou déjà validées.")
        public Mono<ExpenseResponse> update(
                        @Parameter(description = "ID de la dépense") @PathVariable UUID id,
                        @Valid @RequestBody ExpenseUpdateRequest request) {

                ManageExpenseUseCase.UpdateExpenseCommand cmd = new ManageExpenseUseCase.UpdateExpenseCommand(
                                id,
                                request.amount(),
                                request.description());
                return expenseUseCase.update(cmd).map(ExpenseResponse::from);
        }

        // ── EXPORT CSV ────────────────────────────────────────────────────────────

        @GetMapping("/export/csv")
        @PreAuthorize("hasRole('FLEET_MANAGER')")
        @Operation(summary = "Exporter les dépenses en CSV", description = "Génère un fichier CSV de toutes les dépenses approuvées du manager. Compatible Excel (BOM UTF-8).")
        public Mono<ResponseEntity<String>> exportCsv(Authentication auth) {
                Flux<ExpenseResponse> flux = expenseUseCase.getAllByManager(getUserId(auth))
                                .map(ExpenseResponse::from);

                return CsvExportUtil.export(
                                flux,
                                List.of("ID", "Type", "Montant (FCFA)", "Description", "Date",
                                                "Statut", "Source", "Véhicule", "Flotte", "Chauffeur", "Créé le"),
                                e -> List.of(
                                                e.id() != null ? e.id().toString() : "",
                                                e.expenseType() != null ? e.expenseType() : "",
                                                e.amount() != null ? e.amount().toString() : "",
                                                e.description() != null ? e.description() : "",
                                                e.expenseDate() != null ? e.expenseDate().toString() : "",
                                                e.status() != null ? e.status() : "",
                                                e.sourceType() != null ? e.sourceType() : "",
                                                e.vehicleRegistration() != null ? e.vehicleRegistration() : "",
                                                e.fleetId() != null ? e.fleetId().toString() : "",
                                                e.driverFullName() != null ? e.driverFullName() : "",
                                                e.createdAt() != null ? e.createdAt().toString() : ""))
                                .map(csv -> ResponseEntity.ok()
                                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                                                "attachment; filename=\"depenses.csv\"")
                                                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                                                .body(csv));
        }

        // ── SUPPRESSION ───────────────────────────────────────────────────────────

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        @PreAuthorize("hasRole('FLEET_MANAGER')")
        @Operation(summary = "Supprimer une dépense manuelle", description = "Supprime une dépense manuelle. Interdit sur les dépenses auto-générées (FUEL, MAINTENANCE, INCIDENT).")
        public Mono<Void> delete(
                        @Parameter(description = "ID de la dépense") @PathVariable UUID id) {
                return expenseUseCase.delete(id);
        }

        // ── DTOs internes ─────────────────────────────────────────────────────────

        record TotalDto(UUID entityId, java.math.BigDecimal total) {
        }
}
