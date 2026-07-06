package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "trip_mission_submissions", schema = "fleet")
@Data
@NoArgsConstructor
public class TripMissionSubmissionEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("trip_id")
    private UUID tripId;

    @Column("submitted_by")
    private UUID submittedBy;

    @Column("item_type")
    private String itemType;

    private String description;
    private Integer quantity;
    private BigDecimal weight;
    private String notes;
    private String status;

    @Column("reviewed_by")
    private UUID reviewedBy;

    @Column("reviewed_at")
    private Instant reviewedAt;

    @Column("created_at")
    private Instant createdAt;

    @Transient
    private boolean isNew = false;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew || id == null;
    }

    public void setNew(boolean v) {
        this.isNew = v;
    }
}
