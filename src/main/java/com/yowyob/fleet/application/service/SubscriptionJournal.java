package com.yowyob.fleet.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Journal des abonnements d'une entreprise et notification de son administrateur. */
@Service
@RequiredArgsConstructor
public class SubscriptionJournal {

    public record Entry(UUID companyId, String type, UUID actorId, String planName, LocalDate periodEnd,
                        BigDecimal amount, String reference, String reason) {
        public static Entry of(UUID companyId, String type, UUID actorId, String reason) {
            return new Entry(companyId, type, actorId, null, null, null, null, reason);
        }
    }

    private final DatabaseClient db;

    public Mono<Void> log(Entry e) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                INSERT INTO fleet.subscription_events
                    (id, company_id, event_type, actor_id, plan_name, period_end, amount, reference, reason, created_at)
                VALUES (:id, :company, :type, :actor, :plan, :end, :amount, :reference, :reason, :at)
                """)
                .bind("id", UUID.randomUUID())
                .bind("company", e.companyId())
                .bind("type", e.type())
                .bind("at", LocalDateTime.now());
        spec = e.actorId() != null ? spec.bind("actor", e.actorId()) : spec.bindNull("actor", UUID.class);
        spec = e.planName() != null ? spec.bind("plan", e.planName()) : spec.bindNull("plan", String.class);
        spec = e.periodEnd() != null ? spec.bind("end", e.periodEnd()) : spec.bindNull("end", LocalDate.class);
        spec = e.amount() != null ? spec.bind("amount", e.amount()) : spec.bindNull("amount", BigDecimal.class);
        spec = e.reference() != null ? spec.bind("reference", e.reference()) : spec.bindNull("reference", String.class);
        spec = e.reason() != null ? spec.bind("reason", e.reason()) : spec.bindNull("reason", String.class);
        return spec.fetch().rowsUpdated().then();
    }

    /** Notification dans l'application pour l'administrateur de l'entreprise. */
    public Mono<Void> notifyAdmin(UUID companyId, String message) {
        return db.sql("""
                INSERT INTO fleet.notifications (id, user_id, title, message, type, is_read, created_at, data, tenant_id)
                SELECT gen_random_uuid(), x.id, 'Abonnement', :message, 'INFO', FALSE, :now, :data, x.tenant_id
                FROM fleet.users x WHERE x.id = :user
                """)
                .bind("message", message)
                .bind("now", LocalDateTime.now())
                .bind("data", "{\"type\":\"SUBSCRIPTION\"}")
                .bind("user", companyId)
                .fetch().rowsUpdated().then();
    }
}
