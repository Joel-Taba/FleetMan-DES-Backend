package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table(name = "maintenances", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("subject")
    private String subject;

    @Column("cost")
    private BigDecimal cost;

    @Column("date_time")
    private LocalDateTime dateTime;

    @Column("report")
    private String report;

    @Column("longitude")
    private Double longitude;

    @Column("latitude")
    private Double latitude;

    @Column("location_name")
    private String locationName;

    @Column("vehicle_id")
    private UUID vehicleId;

    @Column("vehicle_registration")
    private String vehicleRegistration;

    @Column("driver_id")
    private UUID driverId;

    @Column("driver_full_name")
    private String driverFullName;

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
