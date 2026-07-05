package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("fleet.driver_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverDocumentEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    private UUID driverId;
    private String docType;
    private String docNumber;
    private String licenseCategories;
    private String issuer;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String fileUrl;
    private String status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Transient
    private boolean isNew;

    @Override
    public boolean isNew() { return isNew; }
}
