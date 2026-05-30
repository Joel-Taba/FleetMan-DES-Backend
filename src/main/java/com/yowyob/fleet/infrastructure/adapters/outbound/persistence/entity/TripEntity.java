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
import java.time.LocalTime;
import java.util.UUID;

@Table(name = "trips", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("vehicle_id")
    private UUID vehicleId;

    @Column("driver_id")
    private UUID driverId;

    @Column("vehicle_type_id")
    private UUID vehicleTypeId; // Pour historique

    @Column("start_date")
    private LocalDate startDate;

    @Column("start_time")
    private LocalTime startTime;

    @Column("end_date")
    private LocalDate endDate;

    @Column("end_time")
    private LocalTime endTime;

    private String status; // ONGOING, COMPLETED...

    @Column("distance_km")
    private Double distanceKm;

    @Column("duration_minutes")
    private Integer durationMinutes;

    // --- Gestion R2DBC Persistable ---
    
    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew || id == null;
    }
}