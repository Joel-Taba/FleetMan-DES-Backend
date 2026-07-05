package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table(name = "alert_rules", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("manager_id")
    private UUID managerId;

    @Column("trigger_type")
    private String triggerType;

    @Column("action_type")
    private String actionType;

    @Column("target_role")
    private String targetRole;

    @Column("active")
    private boolean active;

    @Column("system_template")
    private boolean systemTemplate;

    @Column("condition_value")
    private String conditionValue;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() { return isNew || id == null; }

    public void setNew(boolean isNew) { this.isNew = isNew; }
}
