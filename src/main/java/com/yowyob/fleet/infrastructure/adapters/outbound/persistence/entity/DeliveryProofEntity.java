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

@Table(name = "delivery_proofs", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryProofEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("mission_id")
    private UUID missionId;

    @Column("driver_id")
    private UUID driverId;

    @Column("type")
    private String type;

    @Column("status")
    private String status;

    @Column("file_id")
    private String fileId;

    @Column("file_url")
    private String fileUrl;

    @Column("integrity_hash")
    private String integrityHash;

    @Column("recipient_name")
    private String recipientName;

    @Column("recipient_signature")
    private String recipientSignature;

    @Column("latitude")
    private Double latitude;

    @Column("longitude")
    private Double longitude;

    @Column("notes")
    private String notes;

    @Column("submitted_at")
    private LocalDateTime submittedAt;

    @Column("reviewed_at")
    private LocalDateTime reviewedAt;

    @Column("reviewed_by")
    private UUID reviewedBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("tenant_id")
    private UUID tenantId;

    @Transient
    private boolean isNew = false;

    @Override
    public UUID getId() {
        return this.id;
    }

    @Override
    public boolean isNew() {
        return isNew || id == null;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }
}

