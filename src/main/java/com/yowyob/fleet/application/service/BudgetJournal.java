package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.Budget;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Journal des mouvements d'un budget (fleet.budget_events), lu par le grand livre. */
@Service
@RequiredArgsConstructor
public class BudgetJournal {

    private final DatabaseClient db;
    private final AccessScopeService accessScope;

    /** Franchissement d'un seuil dans un sens ou dans l'autre ; rien si le niveau n'a pas changé. */
    public Mono<Void> recordLevelChange(Budget budget, Budget.AlertLevel previous, UUID actorId) {
        Budget.AlertLevel current = budget.getAlertLevel();
        if (previous == current) {
            return Mono.empty();
        }
        String type = current == Budget.AlertLevel.EXCEEDED ? "THRESHOLD_100"
                : current == Budget.AlertLevel.WARNING && previous == Budget.AlertLevel.NORMAL ? "THRESHOLD_80"
                : "RECOVERED";
        return record(budget, type, null, null, actorId, null);
    }

    public Mono<Void> record(Budget budget, String type, BigDecimal oldAmount, BigDecimal newAmount, UUID actorId, String note) {
        return accessScope.resolveLocalUserId(actorId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(actor -> {
                    var spec = db.sql("""
                            INSERT INTO fleet.budget_events (id, budget_id, scope, entity_id, budget_month, event_type,
                                                             old_amount, new_amount, consumed, actor_id, note, created_at, tenant_id)
                            VALUES (gen_random_uuid(), :id, :scope, :entity, :month, :type, :old, :new, :consumed,
                                    :actor, :note, :now, (SELECT tenant_id FROM fleet.budgets WHERE id = :id))
                            """)
                            .bind("id", budget.getId())
                            .bind("scope", budget.getScope().name())
                            .bind("entity", budget.getEntityId())
                            .bind("month", budget.getBudgetMonth())
                            .bind("type", type)
                            .bind("consumed", budget.getConsumed())
                            .bind("now", LocalDateTime.now());
                    spec = oldAmount != null ? spec.bind("old", oldAmount) : spec.bindNull("old", BigDecimal.class);
                    spec = newAmount != null ? spec.bind("new", newAmount) : spec.bindNull("new", BigDecimal.class);
                    spec = actor.isPresent() ? spec.bind("actor", actor.get()) : spec.bindNull("actor", UUID.class);
                    spec = note != null ? spec.bind("note", note) : spec.bindNull("note", String.class);
                    return spec.fetch().rowsUpdated().then();
                });
    }
}
