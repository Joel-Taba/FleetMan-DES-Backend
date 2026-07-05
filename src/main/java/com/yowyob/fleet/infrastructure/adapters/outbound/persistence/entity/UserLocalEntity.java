package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "users", schema = "fleet")
@Getter
@Setter
@ToString
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

    @Column("approval_status")
    @Builder.Default
    private String approvalStatus = "APPROVED";

    @Column("rejection_reason")
    private String rejectionReason;

    @Column("approved_by")
    private UUID approvedBy;

    @Column("approved_at")
    private Instant approvedAt;

    @Column("kernel_id")
    private UUID kernelId;

    @Transient
    @Builder.Default
    private boolean isNewRecord = false;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNewRecord || id == null;
    }

    public void setNew(boolean isNew) {
        this.isNewRecord = isNew;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public void setApprovedBy(UUID approvedBy) {
        this.approvedBy = approvedBy;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public void setKernelId(UUID kernelId) {
        this.kernelId = kernelId;
    }
}
