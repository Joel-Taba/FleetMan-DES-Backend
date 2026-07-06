package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.DriverDocument;
import com.yowyob.fleet.domain.model.VehicleDocument;
import com.yowyob.fleet.domain.ports.out.DocumentPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DriverDocumentEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleDocumentEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverDocumentR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleDocumentR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DocumentPersistenceAdapter implements DocumentPersistencePort {

    private final VehicleDocumentR2dbcRepository vehicleDocRepo;
    private final DriverDocumentR2dbcRepository  driverDocRepo;

    // ── Documents Véhicule ────────────────────────────────────────────────────

    @Override
    public Mono<VehicleDocument> saveVehicleDoc(VehicleDocument doc) {
        VehicleDocumentEntity entity = toVehicleEntity(doc);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setNew(true);
        }
        return vehicleDocRepo.save(entity).map(this::toVehicleDomain);
    }

    @Override
    public Mono<VehicleDocument> findVehicleDocById(UUID id) {
        return vehicleDocRepo.findById(id).map(this::toVehicleDomain);
    }

    @Override
    public Flux<VehicleDocument> findVehicleDocsByVehicleId(UUID vehicleId) {
        return vehicleDocRepo.findByVehicleId(vehicleId).map(this::toVehicleDomain);
    }

    @Override
    public Flux<VehicleDocument> findAllVehicleDocsByManagerId(UUID managerId) {
        return vehicleDocRepo.findAllByManagerId(managerId).map(this::toVehicleDomain);
    }

    @Override
    public Flux<VehicleDocument> findVehicleDocsExpiringBefore(LocalDate date) {
        return vehicleDocRepo.findExpiringBefore(date).map(this::toVehicleDomain);
    }

    @Override
    public Mono<Boolean> existsVehicleDocById(UUID id) {
        return vehicleDocRepo.existsById(id);
    }

    @Override
    public Mono<Void> deleteVehicleDocById(UUID id) {
        return vehicleDocRepo.deleteById(id);
    }

    // ── Documents Conducteur ──────────────────────────────────────────────────

    @Override
    public Mono<DriverDocument> saveDriverDoc(DriverDocument doc) {
        DriverDocumentEntity entity = toDriverEntity(doc);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setNew(true);
        }
        return driverDocRepo.save(entity).map(this::toDriverDomain);
    }

    @Override
    public Mono<DriverDocument> findDriverDocById(UUID id) {
        return driverDocRepo.findById(id).map(this::toDriverDomain);
    }

    @Override
    public Flux<DriverDocument> findDriverDocsByDriverId(UUID driverId) {
        return driverDocRepo.findByDriverId(driverId).map(this::toDriverDomain);
    }

    @Override
    public Flux<DriverDocument> findAllDriverDocsByManagerId(UUID managerId) {
        return driverDocRepo.findAllByManagerId(managerId).map(this::toDriverDomain);
    }

    @Override
    public Flux<DriverDocument> findDriverDocsExpiringBefore(LocalDate date) {
        return driverDocRepo.findExpiringBefore(date).map(this::toDriverDomain);
    }

    @Override
    public Mono<Boolean> existsDriverDocById(UUID id) {
        return driverDocRepo.existsById(id);
    }

    @Override
    public Mono<Void> deleteDriverDocById(UUID id) {
        return driverDocRepo.deleteById(id);
    }

    // ── Conversions Entity ↔ Domain ───────────────────────────────────────────

    private VehicleDocument toVehicleDomain(VehicleDocumentEntity e) {
        return new VehicleDocument(
                e.getId(), e.getVehicleId(),
                VehicleDocument.DocType.valueOf(e.getDocType()),
                e.getDocNumber(), e.getIssuer(),
                e.getIssueDate(), e.getExpiryDate(),
                e.getFileUrl(), e.getFileOriginalName(),
                e.getFileMimeType(), e.getFileSizeBytes(),
                e.getStatus() != null
                        ? VehicleDocument.Status.valueOf(e.getStatus())
                        : VehicleDocument.computeStatus(e.getExpiryDate()),
                e.getNotes(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private VehicleDocumentEntity toVehicleEntity(VehicleDocument d) {
        VehicleDocumentEntity e = new VehicleDocumentEntity();
        e.setId(d.getId());
        e.setVehicleId(d.getVehicleId());
        e.setDocType(d.getDocType().name());
        e.setDocNumber(d.getDocNumber());
        e.setIssuer(d.getIssuer());
        e.setIssueDate(d.getIssueDate());
        e.setExpiryDate(d.getExpiryDate());
        e.setFileUrl(d.getFileUrl());
        e.setFileOriginalName(d.getFileOriginalName());
        e.setFileMimeType(d.getFileMimeType());
        e.setFileSizeBytes(d.getFileSizeBytes());
        e.setStatus(d.getStatus().name());
        e.setNotes(d.getNotes());
        e.setCreatedAt(d.getCreatedAt() != null ? d.getCreatedAt() : LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }

    private DriverDocument toDriverDomain(DriverDocumentEntity e) {
        return new DriverDocument(
                e.getId(), e.getDriverId(),
                DriverDocument.DocType.valueOf(e.getDocType()),
                e.getDocNumber(), e.getLicenseCategories(),
                e.getIssuer(), e.getIssueDate(), e.getExpiryDate(),
                e.getFileUrl(), e.getFileOriginalName(),
                e.getFileMimeType(), e.getFileSizeBytes(),
                e.getStatus() != null
                        ? DriverDocument.Status.valueOf(e.getStatus())
                        : DriverDocument.computeStatus(e.getExpiryDate()),
                e.getNotes(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private DriverDocumentEntity toDriverEntity(DriverDocument d) {
        DriverDocumentEntity e = new DriverDocumentEntity();
        e.setId(d.getId());
        e.setDriverId(d.getDriverId());
        e.setDocType(d.getDocType().name());
        e.setDocNumber(d.getDocNumber());
        e.setLicenseCategories(d.getLicenseCategories());
        e.setIssuer(d.getIssuer());
        e.setIssueDate(d.getIssueDate());
        e.setExpiryDate(d.getExpiryDate());
        e.setFileUrl(d.getFileUrl());
        e.setFileOriginalName(d.getFileOriginalName());
        e.setFileMimeType(d.getFileMimeType());
        e.setFileSizeBytes(d.getFileSizeBytes());
        e.setStatus(d.getStatus().name());
        e.setNotes(d.getNotes());
        e.setCreatedAt(d.getCreatedAt() != null ? d.getCreatedAt() : LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }
}
