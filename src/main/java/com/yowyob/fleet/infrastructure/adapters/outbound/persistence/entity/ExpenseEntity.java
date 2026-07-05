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

@Table(name = "expenses", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("expense_type")
    private String expenseType;

    @Column("amount")
    private BigDecimal amount;

    @Column("description")
    private String description;

    @Column("expense_date")
    private LocalDateTime expenseDate;

    @Column("status")
    private String status;

    @Column("source_type")
    private String sourceType;

    @Column("source_id")
    private UUID sourceId;

    @Column("rejection_reason")
    private String rejectionReason;

    @Column("validated_at")
    private LocalDateTime validatedAt;

    @Column("validated_by")
    private UUID validatedBy;

    @Column("vehicle_id")
    private UUID vehicleId;

    @Column("vehicle_registration")
    private String vehicleRegistration;

    @Column("fleet_id")
    private UUID fleetId;

    @Column("manager_id")
    private UUID managerId;

    @Column("driver_id")
    private UUID driverId;

    @Column("driver_full_name")
    private String driverFullName;

    @Column("created_at")
    private LocalDateTime createdAt;

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
