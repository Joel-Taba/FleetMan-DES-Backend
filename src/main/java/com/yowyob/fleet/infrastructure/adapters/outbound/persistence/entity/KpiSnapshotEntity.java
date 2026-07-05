package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("fleet.kpi_snapshots")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KpiSnapshotEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    private UUID fleetId;
    private String entityType;
    private UUID entityId;
    private String periodType;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    private BigDecimal totalKm;
    private Integer totalTrips;
    private BigDecimal totalDrivingHours;
    private BigDecimal availabilityRate;

    private BigDecimal totalFuelCost;
    private BigDecimal totalFuelLiters;
    private BigDecimal totalMaintenanceCost;
    private BigDecimal totalIncidentCost;
    private BigDecimal costPerKm;
    private BigDecimal fuelPer100km;

    private Integer totalIncidents;
    private BigDecimal incidentRate;
    private BigDecimal avgDriverScore;
    private BigDecimal docComplianceRate;

    private LocalDateTime calculatedAt;

    @Transient
    private boolean isNew;

    @Override
    public boolean isNew() { return isNew; }
}
