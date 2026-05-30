package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table(name = "users", schema = "fleet")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLocalEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    private String username;
    private String email;
    
    @Column("first_name")
    private String firstName;
    
    @Column("last_name")
    private String lastName;

    @Column("photo_url") 
    private String photoUrl;
    
    @Column("is_active")
    @Builder.Default
    private boolean isActive = true;
    
    @Column("last_login_at")
    private Instant lastLoginAt;
    
    @Column("deleted_at")
    private Instant deletedAt;

    @Transient
    @Builder.Default
    private boolean isNewRecord = false;

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() {
        return isNewRecord || id == null;
    }

    public void setNew(boolean isNew) {
        this.isNewRecord = isNew;
    }
}