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
import java.util.UUID;

@Table(name = "trip_details", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripDetailEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Override
    public UUID getId() { return this.id; }

    @Column("trip_id")
    private UUID tripId;

    @Column("item_type")
    private String itemType;

    private String description;

    private int quantity;

    private BigDecimal weight;

    @Column("departure_quantity")
    private Integer departureQuantity;

    @Column("return_quantity")
    private Integer returnQuantity;

    @Column("sort_order")
    private int sortOrder;

    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() { return isNew || id == null; }

    public void setNew(boolean v) { this.isNew = v; }
}
