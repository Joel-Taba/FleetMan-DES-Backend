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

@Table(name = "incidents", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncidentEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Override
    public UUID getId() { return this.id; }

    @Column("type")
    private String type;

    @Column("description")
    private String description;

    @Column("severity")
    private String severity;

    @Column("incident_date_time")
    private LocalDateTime incidentDateTime;

    @Column("longitude")
    private Double longitude;

    @Column("latitude")
    private Double latitude;

    @Column("cost")
    private BigDecimal cost;

    @Column("status")
    private String status;

    @Column("report")
    private String report;

    @Column("witness_name")
    private String witnessName;

    @Column("witness_contact")
    private String witnessContact;

    @Column("police_report_number")
    private String policeReportNumber;

    @Column("insurance_claim_number")
    private String insuranceClaimNumber;

    @Column("reported_by")
    private String reportedBy;

    @Column("resolved_at")
    private LocalDateTime resolvedAt;

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
