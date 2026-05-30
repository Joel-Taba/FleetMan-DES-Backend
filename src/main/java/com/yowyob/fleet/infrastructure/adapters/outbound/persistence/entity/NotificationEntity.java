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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("notifications")
public class NotificationEntity implements Persistable<UUID> {
    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    private String title;
    private String message;
    private String type; // INFO, ALERT...

    @Column("is_read")
    private boolean isRead;

    @Column("created_at")
    private LocalDateTime createdAt;

    // On stocke le JSON sous forme de String pour simplifier le mapping R2DBC standard
    private String data; 

    @Transient
    private boolean newEntity = false;

    @Override
    @Transient
    public boolean isNew() { return newEntity || id == null; }
}