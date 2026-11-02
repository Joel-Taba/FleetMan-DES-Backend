package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Instruction d'un incident (une ligne par incident). */
@Table(name = "incident_cases", schema = "fleet")
@Data
@NoArgsConstructor
public class IncidentCaseEntity implements Persistable<UUID> {

    @Id
    @Column("incident_id")
    private UUID incidentId;

    @Column("location_label")
    private String locationLabel;

    @Column("trip_id")
    private UUID tripId;

    @Column("reported_by_id")
    private UUID reportedById;

    @Column("handler_id")
    private UUID handlerId;

    @Column("estimated_cost")
    private BigDecimal estimatedCost;

    private boolean injuries;

    @Column("injuries_count")
    private Integer injuriesCount;

    @Column("third_party_involved")
    private boolean thirdPartyInvolved;

    @Column("third_party_name")
    private String thirdPartyName;

    @Column("third_party_plate")
    private String thirdPartyPlate;

    @Column("third_party_insurer")
    private String thirdPartyInsurer;

    @Column("third_party_contact")
    private String thirdPartyContact;

    @Column("insurance_status")
    private String insuranceStatus = "NONE";

    @Column("insurer_name")
    private String insurerName;

    @Column("refund_amount")
    private BigDecimal refundAmount;

    @Column("refund_date")
    private LocalDate refundDate;

    @Column("root_cause")
    private String rootCause;

    @Column("corrective_action")
    private String correctiveAction;

    private String responsibility = "UNDETERMINED";

    @Column("maintenance_id")
    private UUID maintenanceId;

    @Column("acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column("closed_at")
    private LocalDateTime closedAt;

    @Column("cancelled_at")
    private LocalDateTime cancelledAt;

    @Column("cancel_reason")
    private String cancelReason;

    @Column("tenant_id")
    private UUID tenantId;

    @Transient
    private boolean isNew = false;

    @Override
    public UUID getId() {
        return incidentId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean v) {
        this.isNew = v;
    }
}
