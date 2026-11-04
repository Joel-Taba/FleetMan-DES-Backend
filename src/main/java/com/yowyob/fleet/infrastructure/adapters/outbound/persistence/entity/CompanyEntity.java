package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Entreprise cliente : son id est celui de son administrateur. Porte l'abonnement. */
@Table(name = "companies", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyEntity implements Persistable<UUID> {
    @Id
    private UUID id;
    private String name;
    @Column("logo_url")
    private String logoUrl;
    @Column("legal_name")
    private String legalName;
    @Column("legal_form")
    private String legalForm;
    @Column("registration_number")
    private String registrationNumber;
    @Column("tax_id")
    private String taxId;
    private String sector;
    private String address;
    private String city;
    private String country;
    @Column("contact_phone")
    private String contactPhone;
    @Column("contact_email")
    private String contactEmail;
    private String website;
    @Column("plan_id")
    private UUID planId;
    @Column("subscription_status")
    private String subscriptionStatus;
    @Column("subscription_start")
    private LocalDate subscriptionStart;
    @Column("subscription_end")
    private LocalDate subscriptionEnd;
    @Column("billing_cycle")
    private String billingCycle;
    @Column("requested_plan_id")
    private UUID requestedPlanId;
    @Column("requested_billing_cycle")
    private String requestedBillingCycle;
    @Column("created_at")
    private Instant createdAt;
    @Column("updated_at")
    private Instant updatedAt;
    @Column("tenant_id")
    private UUID tenantId;

    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew || id == null;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }
}
