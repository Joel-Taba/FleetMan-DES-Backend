package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import java.time.LocalDate;
import java.util.UUID;

@Table(name = "maintenance_parameters", schema = "fleet")
@Data @NoArgsConstructor @AllArgsConstructor
public class MaintenanceParameterEntity implements Persistable<UUID> {
    @Id
    private UUID id;
    
    @Column("vehicle_id")
    private UUID vehicleId;
    
    @Column("last_maintenance_at")
    private LocalDate lastMaintenanceAt;
    
    @Column("next_maintenance_at")
    private LocalDate nextMaintenanceAt;
    
    @Column("engine_status")
    private String engineStatus; 
    
    @Column("battery_health")
    private Integer batteryHealth; // FIX : Changé de String à Integer

    @Column("maintenance_status")
    private String maintenanceStatus;

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