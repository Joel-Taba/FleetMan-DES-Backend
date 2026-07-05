package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("fleet.assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    private UUID scheduleId;
    private UUID fleetId;
    private UUID vehicleId;
    private UUID driverId;
    private UUID missionId;
    private LocalDateTime startDatetime;
    private LocalDateTime endDatetime;
    private String status;
    private String startLocation;
    private String endLocation;
    private BigDecimal estimatedKm;
    private BigDecimal actualKm;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Transient
    private boolean isNew;

    @Override
    public boolean isNew() { return isNew; }
}
