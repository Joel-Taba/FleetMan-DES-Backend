package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.AlertRule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Réponse représentant une règle d'alerte")
public record AlertRuleResponse(
        UUID id,
        String name,
        String description,
        UUID managerId,
        String triggerType,
        String actionType,
        String targetRole,
        boolean active,
        @Schema(description = "Règle fournie par défaut par le système (non supprimable)")
        boolean systemTemplate,
        String conditionValue,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AlertRuleResponse from(AlertRule r) {
        return new AlertRuleResponse(
                r.getId(), r.getName(), r.getDescription(),
                r.getManagerId(),
                r.getTriggerType() != null ? r.getTriggerType().name() : null,
                r.getActionType() != null ? r.getActionType().name() : null,
                r.getTargetRole() != null ? r.getTargetRole().name() : null,
                r.isActive(), r.isSystemTemplate(),
                r.getConditionValue(), r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}
