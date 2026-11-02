package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table(name = "fleet_managers", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FleetManagerEntity implements Persistable<UUID> {
    
    @Id
    @Column("user_id")
    private UUID userId;

    @Column("company_name")
    private String companyName;

    @Column("logo_url")
    private String logoUrl;

    /** Admin (organisation) qui a créé ce gestionnaire. */
    @Column("created_by_admin_id")
    private UUID createdByAdminId;

    /** ACTIVE, SUSPENDED ou CLOSED (écrit par AdminService). */
    @Column("account_status")
    private String accountStatus;

    @Column("suspension_reason")
    private String suspensionReason;

    @Column("status_changed_at")
    private java.time.Instant statusChangedAt;

    @Transient // Ce champ n'est pas en base, il sert à la logique R2DBC
    private boolean isNew = false;

    public FleetManagerEntity(UUID userId, String companyName, UUID createdByAdminId) {
        this.userId = userId;
        this.companyName = companyName;
        this.createdByAdminId = createdByAdminId;
        this.isNew = true;
    }

    @Override
    public UUID getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return isNew || userId == null; // Sécurité si userId est null
    }
}