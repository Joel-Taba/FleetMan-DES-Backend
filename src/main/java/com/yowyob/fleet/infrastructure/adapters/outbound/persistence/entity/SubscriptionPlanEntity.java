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
import java.time.Instant;
import java.util.UUID;

@Table(name = "subscription_plans", schema = "fleet")
@Data @NoArgsConstructor @AllArgsConstructor
public class SubscriptionPlanEntity implements Persistable<UUID> {

    @Id private UUID id;

    @Override public UUID getId() { return this.id; }

    private String name;
    private String description;

    @Column("max_fleets")   private int maxFleets;
    @Column("max_vehicles") private int maxVehicles;
    @Column("max_drivers")  private int maxDrivers;

    @Column("monthly_price") private BigDecimal monthlyPrice;
    @Column("annual_price")  private BigDecimal annualPrice;
    private String currency;
    private String features;

    @Column("is_active")  private boolean isActive;
    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;

    @Transient private boolean isNew = false;
    @Override public boolean isNew() { return isNew || id == null; }
    public void setNew(boolean v) { this.isNew = v; }
}
