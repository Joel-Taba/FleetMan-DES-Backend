package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.CompanyScopeService;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/** Politique de dépenses de l'entreprise : seuil au-delà duquel l'administrateur valide. */
@RestController
@RequestMapping("/api/v1/organization/expense-policy")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_ORGANIZATION)
@SecurityRequirement(name = "bearerAuth")
public class CompanyExpensePolicyController {

    private final CompanyScopeService companyScope;
    private final DatabaseClient db;

    public record ExpensePolicy(@NotNull @DecimalMin("0") BigDecimal approvalThreshold) {}

    private static UUID userId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FLEET_ADMIN', 'FLEET_MANAGER')")
    @Operation(summary = "Seuil d'approbation des dépenses de l'entreprise")
    public Mono<ExpensePolicy> get(Authentication auth) {
        return companyScope.resolveCompanyId(userId(auth))
                .flatMap(companyId -> db.sql("SELECT expense_approval_threshold AS t FROM fleet.companies WHERE id = :c")
                        .bind("c", companyId)
                        .map((row, meta) -> new ExpensePolicy(row.get("t", BigDecimal.class)))
                        .one());
    }

    @PutMapping
    @PreAuthorize("hasRole('FLEET_ADMIN')")
    @Operation(summary = "Modifier le seuil",
               description = "Au-delà de ce montant, une dépense manuelle doit être validée par l'administrateur.")
    public Mono<ExpensePolicy> update(@Valid @RequestBody ExpensePolicy policy, Authentication auth) {
        return companyScope.requireOwnCompany(userId(auth))
                .flatMap(companyId -> db.sql("UPDATE fleet.companies SET expense_approval_threshold = :t, updated_at = NOW() WHERE id = :c")
                        .bind("t", policy.approvalThreshold()).bind("c", companyId)
                        .fetch().rowsUpdated())
                .thenReturn(policy);
    }
}
