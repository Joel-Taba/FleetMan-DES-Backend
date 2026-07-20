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

@Table(name = "drivers", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverEntity implements Persistable<UUID> {
    @Id
    @Column("user_id")
    private UUID userId;
    
    @Column("fleet_id")
    private UUID fleetId;
    
    @Column("licence_number")
    private String licenceNumber;
    
    private String status;
    
    @Column("assigned_vehicle_id")
    private UUID assignedVehicleId;

    @Column("photo_url")
    private String photoUrl;

    @Column("kernel_actor_id")
    private UUID kernelActorId;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("deleted_at")
    private Instant deletedAt;

    @Transient
    private boolean isNew = false;

    // Méthode utilitaire explicite pour forcer l'INSERT
    public void markAsNew() {
        this.isNew = true;
    }

    public void markAsExisting() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        // Si l'ID est null (cas rare ici car on le set manuellement) OU si le flag est true
        return isNew || userId == null;
    }
}