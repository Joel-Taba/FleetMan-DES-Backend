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

    @Transient // Ce champ n'est pas en base, il sert à la logique R2DBC
    private boolean isNew = false;

    // Constructeur utilitaire pour la création
    public FleetManagerEntity(UUID userId, String companyName) {
        this.userId = userId;
        this.companyName = companyName;
        this.isNew = true; // On marque l'objet comme nouveau à la création
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