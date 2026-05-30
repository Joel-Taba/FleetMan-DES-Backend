package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;
import java.util.UUID;

@Table(name = "geofence_events", schema = "fleet")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GeofenceEventEntity {
    @Id
    private UUID id;
    @Column("vehicle_id")
    private UUID vehicleId;
    @Column("zone_id")
    private UUID zoneId;
    private String type; // ENTRY, EXIT
    private Double speed;
    @Column("dwell_time_minutes")
    private Integer dwellTimeMinutes;
    private String severity; // INFO, WARNING, CRITICAL
    @Column("is_read")
    private Boolean isRead;
    private Instant timestamp;
}