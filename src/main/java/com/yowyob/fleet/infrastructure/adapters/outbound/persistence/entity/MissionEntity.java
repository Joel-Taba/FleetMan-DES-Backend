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

@Table(name = "missions", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MissionEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("vehicle_id")
    private UUID vehicleId;

    @Column("driver_id")
    private UUID driverId;

    @Column("fleet_manager_id")
    private UUID fleetManagerId;

    @Column("tiibntick_delivery_id")
    private UUID tiiBnTickDeliveryId;

    @Column("tiibntick_parcel_id")
    private String tiiBnTickParcelId;

    @Column("status")
    private String status;

    @Column("type")
    private String type;

    @Column("scheduled_start")
    private LocalDateTime scheduledStart;

    @Column("scheduled_end")
    private LocalDateTime scheduledEnd;

    @Column("actual_start")
    private LocalDateTime actualStart;

    @Column("actual_end")
    private LocalDateTime actualEnd;

    @Column("start_location_id")
    private UUID startLocationId;

    @Column("end_location_id")
    private UUID endLocationId;

    @Column("geofence_object_id")
    private String geofenceObjectId;

    @Column("notes")
    private String notes;

    @Column("cancel_reason")
    private String cancelReason;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("tenant_id")
    private UUID tenantId;

    @Transient
    private boolean isNew = false;

    @Override
    public UUID getId() {
        return this.id;
    }

    @Override
    public boolean isNew() {
        return isNew || id == null;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }
}

