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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Table(name = "driver_scores", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverScoreEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("driver_id")
    private UUID driverId;

    @Column("fleet_id")
    private UUID fleetId;

    @Column("manager_id")
    private UUID managerId;

    @Column("period_type")
    private String periodType;

    @Column("period_start")
    private LocalDate periodStart;

    @Column("period_end")
    private LocalDate periodEnd;

    // ── Données brutes ────────────────────────────────────────────────────────

    @Column("incident_count")
    private int incidentCount;

    @Column("total_trips")
    private int totalTrips;

    @Column("fuel_per_100km")
    private BigDecimal fuelPer100Km;

    @Column("fleet_avg_fuel_per_100km")
    private BigDecimal fleetAvgFuelPer100Km;

    @Column("doc_compliance_rate")
    private BigDecimal docComplianceRate;

    @Column("abnormal_maintenance_count")
    private int abnormalMaintenanceCount;

    @Column("completed_assignments")
    private int completedAssignments;

    @Column("no_show_assignments")
    private int noShowAssignments;

    // ── Scores composantes ────────────────────────────────────────────────────

    @Column("incident_score")
    private BigDecimal incidentScore;

    @Column("fuel_score")
    private BigDecimal fuelScore;

    @Column("compliance_score")
    private BigDecimal complianceScore;

    @Column("punctuality_score")
    private BigDecimal punctualityScore;

    @Column("maintenance_score")
    private BigDecimal maintenanceScore;

    // ── Score final ───────────────────────────────────────────────────────────

    @Column("final_score")
    private BigDecimal finalScore;

    @Column("badge")
    private String badge;

    @Column("calculated_at")
    private LocalDateTime calculatedAt;

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
