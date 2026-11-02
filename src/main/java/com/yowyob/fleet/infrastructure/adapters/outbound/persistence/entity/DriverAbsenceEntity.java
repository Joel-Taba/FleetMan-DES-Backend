package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import com.yowyob.fleet.domain.model.DriverAbsence;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Table(name = "driver_absences", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverAbsenceEntity implements Persistable<UUID> {
    @Id
    private UUID id;

    @Column("driver_id")
    private UUID driverId;

    private String type;

    @Column("start_date")
    private LocalDate startDate;

    @Column("end_date")
    private LocalDate endDate;

    private String reason;

    @Column("created_by")
    private UUID createdBy;

    @Column("created_at")
    private Instant createdAt;

    @Column("cancelled_at")
    private Instant cancelledAt;

    @Column("tenant_id")
    private UUID tenantId;

    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew || id == null;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    public DriverAbsence toDomain() {
        return new DriverAbsence(id, driverId, type, startDate, endDate, reason, createdBy, createdAt, cancelledAt);
    }
}
