package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.DocumentException;
import com.yowyob.fleet.domain.model.DriverDocument;
import com.yowyob.fleet.domain.model.VehicleDocument;
import com.yowyob.fleet.domain.ports.in.ManageDocumentUseCase;
import com.yowyob.fleet.domain.ports.out.DocumentPersistencePort;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.VehiclePersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService implements ManageDocumentUseCase {

    private final DocumentPersistencePort documentPort;
    private final VehiclePersistencePort vehiclePort;
    private final DriverPersistencePort driverPort;

    // ── Documents Véhicule ────────────────────────────────────────────────────

    @Override
    public Mono<VehicleDocument> addVehicleDocument(AddVehicleDocumentCommand cmd) {
        return vehiclePort.getLocalDataById(cmd.vehicleId())
                .switchIfEmpty(Mono.error(DocumentException.vehicleNotFound(cmd.vehicleId())))
                .flatMap(vehicle -> {
                    VehicleDocument.DocType type;
                    try {
                        type = VehicleDocument.DocType.valueOf(cmd.docType());
                    } catch (IllegalArgumentException e) {
                        return Mono.error(DocumentException.invalidDocType(cmd.docType()));
                    }

                    VehicleDocument doc = new VehicleDocument(
                            null, cmd.vehicleId(), type,
                            cmd.docNumber(), cmd.issuer(),
                            cmd.issueDate(), cmd.expiryDate(),
                            cmd.fileUrl(), cmd.fileOriginalName(),
                            cmd.fileMimeType(), cmd.fileSizeBytes(),
                            null, cmd.notes(),
                            null, null
                    );
                    return documentPort.saveVehicleDoc(doc);
                })
                .doOnSuccess(d -> log.info("Document véhicule ajouté : type={}, véhicule={}",
                        d.getDocType(), d.getVehicleId()));
    }

    @Override
    public Mono<VehicleDocument> getVehicleDocById(UUID id) {
        return documentPort.findVehicleDocById(id)
                .switchIfEmpty(Mono.error(DocumentException.vehicleDocNotFound(id)));
    }

    @Override
    public Flux<VehicleDocument> getVehicleDocuments(UUID vehicleId) {
        return documentPort.findVehicleDocsByVehicleId(vehicleId);
    }

    @Override
    public Mono<VehicleDocument> updateVehicleDocument(UpdateVehicleDocumentCommand cmd) {
        return documentPort.findVehicleDocById(cmd.documentId())
                .switchIfEmpty(Mono.error(DocumentException.vehicleDocNotFound(cmd.documentId())))
                .flatMap(doc -> {
                    if (cmd.fileUrl() != null)   doc.setFileUrl(cmd.fileUrl());
                    if (cmd.fileOriginalName() != null || cmd.fileMimeType() != null || cmd.fileSizeBytes() != null) {
                        doc.setFileMetadata(cmd.fileOriginalName(), cmd.fileMimeType(), cmd.fileSizeBytes());
                    }
                    if (cmd.notes() != null)     doc.setNotes(cmd.notes());
                    if (cmd.status() != null) {
                        try {
                            doc.setStatus(VehicleDocument.Status.valueOf(cmd.status()));
                        } catch (IllegalArgumentException ignored) {}
                    }
                    // Recalcul du statut si pas de statut forcé
                    if (cmd.status() == null) doc.refreshStatus();
                    return documentPort.saveVehicleDoc(doc);
                });
    }

    @Override
    public Mono<Void> deleteVehicleDocument(UUID id) {
        return documentPort.existsVehicleDocById(id)
                .flatMap(exists -> {
                    if (!exists) return Mono.error(DocumentException.vehicleDocNotFound(id));
                    return documentPort.deleteVehicleDocById(id);
                });
    }

    // ── Documents Conducteur ──────────────────────────────────────────────────

    @Override
    public Mono<DriverDocument> addDriverDocument(AddDriverDocumentCommand cmd) {
        return driverPort.findById(cmd.driverId())
                .switchIfEmpty(Mono.error(DocumentException.driverNotFound(cmd.driverId())))
                .flatMap(driver -> {
                    DriverDocument.DocType type;
                    try {
                        type = DriverDocument.DocType.valueOf(cmd.docType());
                    } catch (IllegalArgumentException e) {
                        return Mono.error(DocumentException.invalidDocType(cmd.docType()));
                    }

                    DriverDocument doc = new DriverDocument(
                            null, cmd.driverId(), type,
                            cmd.docNumber(), cmd.licenseCategories(),
                            cmd.issuer(), cmd.issueDate(), cmd.expiryDate(),
                            cmd.fileUrl(), cmd.fileOriginalName(),
                            cmd.fileMimeType(), cmd.fileSizeBytes(),
                            null, cmd.notes(),
                            null, null
                    );
                    return documentPort.saveDriverDoc(doc);
                })
                .doOnSuccess(d -> log.info("Document conducteur ajouté : type={}, conducteur={}",
                        d.getDocType(), d.getDriverId()));
    }

    @Override
    public Mono<DriverDocument> getDriverDocById(UUID id) {
        return documentPort.findDriverDocById(id)
                .switchIfEmpty(Mono.error(DocumentException.driverDocNotFound(id)));
    }

    @Override
    public Flux<DriverDocument> getDriverDocuments(UUID driverId) {
        return documentPort.findDriverDocsByDriverId(driverId);
    }

    @Override
    public Mono<DriverDocument> updateDriverDocument(UpdateDriverDocumentCommand cmd) {
        return documentPort.findDriverDocById(cmd.documentId())
                .switchIfEmpty(Mono.error(DocumentException.driverDocNotFound(cmd.documentId())))
                .flatMap(doc -> {
                    if (cmd.fileUrl() != null) doc.setFileUrl(cmd.fileUrl());
                    if (cmd.fileOriginalName() != null || cmd.fileMimeType() != null || cmd.fileSizeBytes() != null) {
                        doc.setFileMetadata(cmd.fileOriginalName(), cmd.fileMimeType(), cmd.fileSizeBytes());
                    }
                    if (cmd.notes() != null)   doc.setNotes(cmd.notes());
                    if (cmd.status() != null) {
                        try {
                            doc.setStatus(DriverDocument.Status.valueOf(cmd.status()));
                        } catch (IllegalArgumentException ignored) {}
                    }
                    if (cmd.status() == null) doc.refreshStatus();
                    return documentPort.saveDriverDoc(doc);
                });
    }

    @Override
    public Mono<Void> deleteDriverDocument(UUID id) {
        return documentPort.existsDriverDocById(id)
                .flatMap(exists -> {
                    if (!exists) return Mono.error(DocumentException.driverDocNotFound(id));
                    return documentPort.deleteDriverDocById(id);
                });
    }

    // ── Vues transversales ────────────────────────────────────────────────────

    @Override
    public Flux<ExpiringDocumentDto> getExpiringDocuments(UUID managerId, int withinDays) {
        LocalDate threshold = LocalDate.now().plusDays(withinDays);

        Flux<ExpiringDocumentDto> vehicleDocs = documentPort
                .findAllVehicleDocsByManagerId(managerId)
                .filter(d -> d.getExpiryDate() != null
                        && !d.getExpiryDate().isAfter(threshold))
                .map(d -> new ExpiringDocumentDto(
                        d.getId(), "VEHICLE", d.getVehicleId(),
                        d.getVehicleId().toString(),
                        d.getDocType().name(), d.getDocNumber(),
                        d.getExpiryDate(), d.daysUntilExpiry(),
                        d.getStatus().name()
                ));

        Flux<ExpiringDocumentDto> driverDocs = documentPort
                .findAllDriverDocsByManagerId(managerId)
                .filter(d -> d.getExpiryDate() != null
                        && !d.getExpiryDate().isAfter(threshold))
                .map(d -> new ExpiringDocumentDto(
                        d.getId(), "DRIVER", d.getDriverId(),
                        d.getDriverId().toString(),
                        d.getDocType().name(), d.getDocNumber(),
                        d.getExpiryDate(), d.daysUntilExpiry(),
                        d.getStatus().name()
                ));

        return Flux.merge(vehicleDocs, driverDocs)
                .sort((a, b) -> Long.compare(a.daysUntilExpiry(), b.daysUntilExpiry()));
    }

    @Override
    public Flux<ExpiringDocumentDto> getExpiredDocuments(UUID managerId) {
        return getExpiringDocuments(managerId, 0)
                .filter(d -> d.daysUntilExpiry() < 0);
    }

    @Override
    public Mono<ComplianceReportDto> getComplianceReport(UUID managerId) {
        Flux<VehicleDocument> vDocs = documentPort.findAllVehicleDocsByManagerId(managerId);
        Flux<DriverDocument>  dDocs = documentPort.findAllDriverDocsByManagerId(managerId);

        return Flux.merge(
                        vDocs.map(d -> d.getStatus().name()),
                        dDocs.map(d -> d.getStatus().name())
                )
                .collectList()
                .map(statuses -> {
                    int total       = statuses.size();
                    int valid       = (int) statuses.stream().filter(s -> s.equals("VALID")).count();
                    int expiring    = (int) statuses.stream().filter(s -> s.equals("EXPIRING_SOON")).count();
                    int expired     = (int) statuses.stream().filter(s -> s.equals("EXPIRED")).count();
                    double rate     = total > 0 ? (double) valid / total * 100.0 : 100.0;
                    return new ComplianceReportDto(managerId, total, valid, expiring, expired, rate);
                });
    }
}
