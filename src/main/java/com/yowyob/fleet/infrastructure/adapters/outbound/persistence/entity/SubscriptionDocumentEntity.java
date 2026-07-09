package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Table(name = "subscription_documents", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDocumentEntity implements Persistable<UUID> {

    @Id private UUID id;
    @Column("user_id") private UUID userId;
    @Column("doc_type") private String docType;
    @Column("doc_number") private String docNumber;
    @Column("file_url") private String fileUrl;
    @Column("file_mime_type") private String fileMimeType;
    @Column("file_original_name") private String fileOriginalName;
    @Column("expiry_date") private LocalDate expiryDate;
    private String issuer;
    @Column("issue_date") private LocalDate issueDate;
    private String notes;
    @Column("license_categories") private String licenseCategories;
    @Column("created_at") private Instant createdAt;

    @Transient private boolean isNew = false;

    @Override public UUID getId() { return id; }
    @Override public boolean isNew() { return isNew || id == null; }
}
