package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.AlertRule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Requête de création d'une règle d'alerte personnalisée")
public record AlertRuleRequest(

        @NotBlank(message = "Le nom de la règle est obligatoire.")
        @Schema(description = "Nom lisible de la règle", example = "Document expirant sous 15j")
        String name,

        @Schema(description = "Description optionnelle", example = "Alerte 15 jours avant expiration de tout document légal")
        String description,

        @NotNull(message = "Le type de déclencheur est obligatoire.")
        @Schema(description = "Type de déclencheur",
                allowableValues = {"DOCUMENT_EXPIRY","BUDGET_THRESHOLD","MAINTENANCE_ALERT_DUE",
                        "DRIVER_SCORE_DROP","FUEL_ANOMALY","INCIDENT_REPORTED","TRIP_OVERDUE"})
        AlertRule.TriggerType triggerType,

        @NotNull(message = "Le type d'action est obligatoire.")
        @Schema(description = "Canal de notification", allowableValues = {"IN_APP_NOTIFICATION","EMAIL"})
        AlertRule.ActionType actionType,

        @NotNull(message = "Le destinataire est obligatoire.")
        @Schema(description = "Rôle destinataire", allowableValues = {"MANAGER","ADMIN","DRIVER"})
        AlertRule.TargetRole targetRole,

        @Schema(description = "Valeur seuil de la condition (ex: '30' pour 30 jours, '80' pour 80%)",
                example = "30")
        String conditionValue
) {}
