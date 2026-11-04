package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

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

@Table(name = "trip_stops", schema = "fleet")
@Data
@NoArgsConstructor
public class TripStopEntity implements Persistable<UUID> {
    @Id
    private UUID id;
    @Column("trip_id")
    private UUID tripId;
    private int sequence;
    @Column("stop_type")
    private String stopType;
    private String location;
    private BigDecimal latitude;
    private BigDecimal longitude;
    @Column("contact_name")
    private String contactName;
    @Column("contact_phone")
    private String contactPhone;
    private String instructions;
    @Column("planned_arrival")
    private LocalDateTime plannedArrival;
    private String status;
    @Column("arrived_at")
    private LocalDateTime arrivedAt;
    @Column("completed_at")
    private LocalDateTime completedAt;
    @Column("recipient_name")
    private String recipientName;
    @Column("proof_url")
    private String proofUrl;
    private String notes;
    @Column("failure_reason")
    private String failureReason;
    @Column("failure_notes")
    private String failureNotes;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;
    @Column("tenant_id")
    private UUID tenantId;

    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew;
    }
}
