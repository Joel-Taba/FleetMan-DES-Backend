package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Table(name = "maintenance_alerts", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceAlertEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("plan_id")
    private UUID planId;

    @Column("maintenance_type")
    private String maintenanceType;

    @Column("vehicle_id")
    private UUID vehicleId;

    @Column("vehicle_registration")
    private String vehicleRegistration;

    @Column("fleet_id")
    private UUID fleetId;

    @Column("manager_id")
    private UUID managerId;

    @Column("status")
    private String status;

    @Column("trigger_type")
    private String triggerType;

    @Column("last_maintenance_km")
    private Float lastMaintenanceKm;

    @Column("target_km")
    private Float targetKm;

    @Column("current_km")
    private Float currentKm;

    @Column("km_remaining")
    private Float kmRemaining;

    @Column("last_maintenance_date")
    private LocalDate lastMaintenanceDate;

    @Column("target_date")
    private LocalDate targetDate;

    @Column("days_remaining")
    private Integer daysRemaining;

    @Column("resolved_by_maintenance_id")
    private UUID resolvedByMaintenanceId;

    @Column("resolved_at")
    private LocalDateTime resolvedAt;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

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
