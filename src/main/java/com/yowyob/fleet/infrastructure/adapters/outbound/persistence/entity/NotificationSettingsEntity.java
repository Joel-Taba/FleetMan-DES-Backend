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

@Table("notification_settings")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class NotificationSettingsEntity implements Persistable<UUID> {

    @Id
    @Column("user_id")
    private UUID userId;

    @Column("enable_email")
    private boolean enableEmail;

    @Column("enable_sms")
    private boolean enableSms;

    @Column("enable_push")
    private boolean enablePush;

    @Column("enable_whatsapp")
    private boolean enableWhatsapp;

    @Transient
    private boolean newEntity = false;

    @Override
    @Transient
    public UUID getId() {
        return userId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return newEntity || userId == null;
    }
}