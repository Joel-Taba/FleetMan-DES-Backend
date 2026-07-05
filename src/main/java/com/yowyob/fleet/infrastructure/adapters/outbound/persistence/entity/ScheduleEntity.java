package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("fleet.schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    private UUID fleetId;
    private UUID managerId;
    private String title;
    private String periodType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID createdBy;

    @Transient
    private boolean isNew;

    @Override
    public boolean isNew() { return isNew; }
}
