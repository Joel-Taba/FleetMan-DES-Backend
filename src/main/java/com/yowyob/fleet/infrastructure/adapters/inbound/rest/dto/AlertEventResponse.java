package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.AlertEvent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Réponse représentant une notification d'alerte in-app")
public record AlertEventResponse(
        UUID id,
        UUID ruleId,
        String ruleName,
        UUID managerId,
        String triggerType,
        String actionType,

        @Schema(description = "Titre court de la notification", example = "Document expirant")
        String title,

        @Schema(description = "Message détaillé", example = "L'assurance du véhicule CE-001-AA expire dans 7 jours.")
        String message,

        UUID sourceEntityId,
        String sourceEntityType,

        @Schema(description = "Statut de lecture (UNREAD, READ, DISMISSED)")
        String readStatus,

        boolean unread,
        LocalDateTime sentAt,
        LocalDateTime readAt
) {
    public static AlertEventResponse from(AlertEvent e) {
        return new AlertEventResponse(
                e.getId(), e.getRuleId(), e.getRuleName(),
                e.getManagerId(),
                e.getTriggerType() != null ? e.getTriggerType().name() : null,
                e.getActionType() != null ? e.getActionType().name() : null,
                e.getTitle(), e.getMessage(),
                e.getSourceEntityId(), e.getSourceEntityType(),
                e.getReadStatus() != null ? e.getReadStatus().name() : null,
                e.isUnread(),
                e.getSentAt(), e.getReadAt()
        );
    }
}
