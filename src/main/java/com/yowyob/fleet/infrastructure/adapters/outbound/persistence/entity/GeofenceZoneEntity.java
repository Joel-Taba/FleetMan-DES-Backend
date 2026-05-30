package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;
@Table(name = "geofence_zones", schema = "fleet")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GeofenceZoneEntity implements Persistable<UUID> {
    @Id
    private UUID id;
    
    @Column("manager_id")
    private UUID managerId; // Le propriétaire
    
    @Column("fleet_id")
    private UUID fleetId; // L'affectation (nullable)

    @Column("zone_type")
    private String zoneType; // "p" pour polygone, "c" pour cercle

    @Transient @Builder.Default
    private boolean isNew = false;

    @Override public UUID getId() { return id; }
    @Override public boolean isNew() { return isNew || id == null; }
    public void markNew() { this.isNew = true; }
}