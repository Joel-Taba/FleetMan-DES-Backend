package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/** Site de l'entreprise : siège, dépôt, agence ou atelier. */
@Table(name = "company_sites", schema = "fleet")
@Data
@NoArgsConstructor
public class CompanySiteEntity implements Persistable<UUID> {
    @Id
    private UUID id;
    @Column("company_id")
    private UUID companyId;
    private String name;
    @Column("site_type")
    private String siteType;
    private String address;
    private String city;
    private String phone;
    private boolean active = true;
    @Column("created_at")
    private Instant createdAt;
    @Column("updated_at")
    private Instant updatedAt;

    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew;
    }
}
