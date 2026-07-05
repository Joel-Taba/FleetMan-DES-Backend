package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.Budget;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Réponse représentant un budget mensuel avec son état de consommation")
public record BudgetResponse(

        @Schema(description = "Identifiant unique du budget")
        UUID id,

        @Schema(description = "Portée du budget (FLEET ou VEHICLE)")
        String scope,

        @Schema(description = "ID de la flotte ou du véhicule ciblé")
        UUID entityId,

        @Schema(description = "ID du manager propriétaire")
        UUID managerId,

        @Schema(description = "Mois du budget (format YYYY-MM-01)")
        LocalDate budgetMonth,

        @Schema(description = "Montant plafond en FCFA")
        BigDecimal amount,

        @Schema(description = "Montant consommé en FCFA")
        BigDecimal consumed,

        @Schema(description = "Montant restant en FCFA")
        BigDecimal remaining,

        @Schema(description = "Taux de consommation en % (0-100+)")
        BigDecimal consumptionRate,

        @Schema(description = "Niveau d'alerte courant (NORMAL, WARNING, EXCEEDED)")
        String alertLevel,

        @Schema(description = "Indique si le budget est dépassé")
        boolean exceeded,

        @Schema(description = "Alerte 80% déjà envoyée")
        boolean alert80Sent,

        @Schema(description = "Alerte 100% déjà envoyée")
        boolean alert100Sent,

        @Schema(description = "Notes / contexte du budget")
        String notes,

        @Schema(description = "Date de création")
        LocalDateTime createdAt,

        @Schema(description = "Dernière mise à jour")
        LocalDateTime updatedAt
) {

    /**
     * Méthode de fabrique : construit la réponse depuis le modèle domaine.
     * Calcule automatiquement remaining et consumptionRate via les méthodes métier.
     */
    public static BudgetResponse from(Budget b) {
        return new BudgetResponse(
                b.getId(),
                b.getScope() != null ? b.getScope().name() : null,
                b.getEntityId(),
                b.getManagerId(),
                b.getBudgetMonth(),
                b.getAmount(),
                b.getConsumed(),
                b.remaining(),
                b.consumptionRate(),
                b.getAlertLevel() != null ? b.getAlertLevel().name() : null,
                b.isExceeded(),
                b.isAlert80Sent(),
                b.isAlert100Sent(),
                b.getNotes(),
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }
}
