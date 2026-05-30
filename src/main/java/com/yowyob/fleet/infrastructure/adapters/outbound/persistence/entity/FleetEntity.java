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
import java.util.UUID;

@Table(name = "fleets", schema = "fleet")
@Data @NoArgsConstructor @AllArgsConstructor
public class FleetEntity implements Persistable<UUID> {
    @Id
    private UUID id;
    
    @Column("manager_id")
    private UUID managerId;
    
    private String name;
    
    @Column("phone_number")
    private String phoneNumber;
    
    @Column("created_at")
    private Instant createdAt;

    // --- R2DBC Persistable Logic ---

    @Transient // Ce champ n'est pas mappé en base
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        // Si isNew est true OU si l'id est null, on considère que c'est une création
        return isNew || id == null;
    }
}