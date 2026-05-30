package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import java.time.LocalDate;
import java.util.UUID;

@Table(name = "financial_parameters", schema = "fleet")
@Data @NoArgsConstructor @AllArgsConstructor
public class FinancialParameterEntity implements Persistable<UUID> {
    @Id
    private UUID id;
    
    @Column("vehicle_id")
    private UUID vehicleId;
    
    @Column("insurance_number")
    private String insuranceNumber;
    
    @Column("insurance_expired_at")
    private LocalDate insuranceExpiredAt;
    
    @Column("registered_at")
    private LocalDate registeredAt;
    
    @Column("purchased_at")
    private LocalDate purchasedAt;
    
    @Column("depreciation_rate")
    private Integer depreciationRate;
    
    @Column("cost_per_km")
    private Float costPerKm;

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