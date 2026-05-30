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

@Table(name = "fleetmanager_geofence_zones", schema = "fleet")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FleetManagerGeofenceZoneEntity implements Persistable<UUID> {
    
    @Id
    @Column("fleet_manager_id")
    private UUID fleetManagerId;
    
    @Column("zone_id")
    private UUID zoneId;
    
    @Transient
    @Builder.Default
    private boolean isNew = true;
    
    @Override
    public UUID getId() {
        return fleetManagerId;
    }
    
    @Override
    public boolean isNew() {
        return isNew;
    }
}
