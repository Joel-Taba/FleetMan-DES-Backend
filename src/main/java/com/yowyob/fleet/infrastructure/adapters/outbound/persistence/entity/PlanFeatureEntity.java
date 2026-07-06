package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table(name = "plan_features", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanFeatureEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("plan_id")
    private UUID planId;

    @Column("feature_key")
    private String featureKey;

    @Column("feature_label")
    private String featureLabel;

    private boolean enabled;

    @Transient
    private boolean isNew;

    @Override
    public boolean isNew() {
        return isNew || id == null;
    }
}
