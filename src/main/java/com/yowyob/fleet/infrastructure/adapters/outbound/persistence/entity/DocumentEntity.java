package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import com.yowyob.fleet.domain.model.DocumentRules;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Pièce d'un dossier (véhicule, chauffeur ou gestionnaire), une ligne par version déposée. */
@Table(name = "documents", schema = "fleet")
@Data
@NoArgsConstructor
public class DocumentEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("target_type")
    private String targetType;

    @Column("target_id")
    private UUID targetId;

    @Column("doc_type")
    private String docType;

    @Column("doc_number")
    private String docNumber;

    @Column("license_categories")
    private String licenseCategories;

    private String issuer;

    @Column("issue_date")
    private LocalDate issueDate;

    @Column("expiry_date")
    private LocalDate expiryDate;

    @Column("file_url")
    private String fileUrl;

    @Column("file_original_name")
    private String fileOriginalName;

    @Column("file_mime_type")
    private String fileMimeType;

    @Column("file_size_bytes")
    private Long fileSizeBytes;

    @Column("kernel_file_id")
    private UUID kernelFileId;

    @Column("kernel_link_id")
    private UUID kernelLinkId;

    @Column("review_status")
    private String reviewStatus;

    @Column("rejection_reason")
    private String rejectionReason;

    @Column("reviewed_by")
    private UUID reviewedBy;

    @Column("reviewed_at")
    private LocalDateTime reviewedAt;

    private int version;

    @Column("is_current")
    private boolean current;

    @Column("superseded_by")
    private UUID supersededBy;

    @Column("superseded_at")
    private LocalDateTime supersededAt;

    @Column("archived_at")
    private LocalDateTime archivedAt;

    @Column("archive_reason")
    private String archiveReason;

    private String notes;

    @Column("submitted_by")
    private UUID submittedBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("tenant_id")
    private UUID tenantId;

    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew || id == null;
    }

    public void setNew(boolean v) {
        this.isNew = v;
    }

    public DocumentRules.Piece toPiece() {
        return new DocumentRules.Piece(docType, reviewStatus, current, expiryDate);
    }

    public String effectiveStatus(LocalDate today) {
        return DocumentRules.effectiveStatus(reviewStatus, archivedAt != null, supersededBy != null, expiryDate, today);
    }
}
