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

@Table(name = "budgets", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BudgetEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("scope")
    private String scope;

    @Column("entity_id")
    private UUID entityId;

    @Column("manager_id")
    private UUID managerId;

    @Column("budget_month")
    private LocalDate budgetMonth;

    @Column("amount")
    private BigDecimal amount;

    @Column("consumed")
    private BigDecimal consumed;

    @Column("alert_level")
    private String alertLevel;

    @Column("alert_80_sent")
    private boolean alert80Sent;

    @Column("alert_100_sent")
    private boolean alert100Sent;

    @Column("notes")
    private String notes;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

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
