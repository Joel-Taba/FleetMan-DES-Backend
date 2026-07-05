package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.Expense;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Réponse représentant une dépense opérationnelle")
public record ExpenseResponse(

        @Schema(description = "Identifiant unique de la dépense")
        UUID id,

        @Schema(description = "Type de dépense")
        String expenseType,

        @Schema(description = "Montant en FCFA")
        BigDecimal amount,

        @Schema(description = "Description de la dépense")
        String description,

        @Schema(description = "Date et heure de la dépense")
        LocalDateTime expenseDate,

        @Schema(description = "Statut de validation (PENDING, APPROVED, REJECTED)")
        String status,

        @Schema(description = "Source de création (AUTO ou MANUAL)")
        String sourceType,

        @Schema(description = "ID de l'opération source (si AUTO)")
        UUID sourceId,

        @Schema(description = "Motif de rejet (si REJECTED)")
        String rejectionReason,

        @Schema(description = "Date de validation ou rejet")
        LocalDateTime validatedAt,

        @Schema(description = "ID du manager ayant validé")
        UUID validatedBy,

        @Schema(description = "ID du véhicule concerné")
        UUID vehicleId,

        @Schema(description = "Numéro d'immatriculation du véhicule")
        String vehicleRegistration,

        @Schema(description = "ID de la flotte")
        UUID fleetId,

        @Schema(description = "ID du manager")
        UUID managerId,

        @Schema(description = "ID du chauffeur impliqué (optionnel)")
        UUID driverId,

        @Schema(description = "Nom complet du chauffeur (optionnel)")
        String driverFullName,

        @Schema(description = "Date de création")
        LocalDateTime createdAt
) {

    /**
     * Méthode de fabrique : construit la réponse depuis le modèle domaine.
     */
    public static ExpenseResponse from(Expense e) {
        return new ExpenseResponse(
                e.getId(),
                e.getExpenseType() != null ? e.getExpenseType().name() : null,
                e.getAmount(),
                e.getDescription(),
                e.getExpenseDate(),
                e.getStatus() != null ? e.getStatus().name() : null,
                e.getSourceType() != null ? e.getSourceType().name() : null,
                e.getSourceId(),
                e.getRejectionReason(),
                e.getValidatedAt(),
                e.getValidatedBy(),
                e.getVehicleId(),
                e.getVehicleRegistration(),
                e.getFleetId(),
                e.getManagerId(),
                e.getDriverId(),
                e.getDriverFullName(),
                e.getCreatedAt()
        );
    }
}
