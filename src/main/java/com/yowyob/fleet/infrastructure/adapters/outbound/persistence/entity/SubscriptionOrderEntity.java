package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Commande d'abonnement : séparée de son règlement, payée au plus une fois. */
@Data
@NoArgsConstructor
@Table(name = "subscription_orders", schema = "fleet")
public class SubscriptionOrderEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    /** Archive : commandes passées avant le modèle entreprise (056). */
    @Column("manager_id") private UUID managerId;
    @Column("company_id") private UUID companyId;
    @Column("plan_id") private UUID planId;
    @Column("plan_name") private String planName;
    @Column("billing_cycle") private String billingCycle;
    private BigDecimal amount;
    private String currency;
    private String status;
    @Column("payment_provider") private String paymentProvider;
    @Column("payment_reference") private String paymentReference;
    @Column("payment_method") private String paymentMethod;
    @Column("recorded_by") private UUID recordedBy;
    @Column("payment_note") private String paymentNote;
    @Column("period_start") private LocalDate periodStart;
    @Column("period_end") private LocalDate periodEnd;
    @Column("created_at") private LocalDateTime createdAt;
    @Column("paid_at") private LocalDateTime paidAt;
    @Column("updated_at") private LocalDateTime updatedAt;

    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew || id == null;
    }
}
