package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table(name = "maintenance_plans", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaintenancePlanEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("maintenance_type")
    private String maintenanceType;

    @Column("scope")
    private String scope;

    @Column("fleet_id")
    private UUID fleetId;

    @Column("vehicle_id")
    private UUID vehicleId;

    @Column("manager_id")
    private UUID managerId;

    @Column("label")
    private String label;

    @Column("description")
    private String description;

    @Column("interval_km")
    private Integer intervalKm;

    @Column("interval_days")
    private Integer intervalDays;

    @Column("pre_alert_km")
    private Integer preAlertKm;

    @Column("pre_alert_days")
    private Integer preAlertDays;

    @Column("active")
    private boolean active;

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
