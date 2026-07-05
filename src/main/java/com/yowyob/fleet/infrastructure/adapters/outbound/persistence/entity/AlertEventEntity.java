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

@Table(name = "alert_events", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertEventEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("rule_id")
    private UUID ruleId;

    @Column("rule_name")
    private String ruleName;

    @Column("manager_id")
    private UUID managerId;

    @Column("trigger_type")
    private String triggerType;

    @Column("action_type")
    private String actionType;

    @Column("title")
    private String title;

    @Column("message")
    private String message;

    @Column("source_entity_id")
    private UUID sourceEntityId;

    @Column("source_entity_type")
    private String sourceEntityType;

    @Column("read_status")
    private String readStatus;

    @Column("sent_at")
    private LocalDateTime sentAt;

    @Column("read_at")
    private LocalDateTime readAt;

    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() { return isNew || id == null; }

    public void setNew(boolean isNew) { this.isNew = isNew; }
}
