package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.exception.VehicleException;
import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.ports.out.VehiclePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.*;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.*;
import com.yowyob.fleet.infrastructure.mappers.VehicleLocalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VehiclePersistenceAdapter implements VehiclePersistencePort {

    private final VehicleLocalR2dbcRepository vehicleRepo;
    private final FinancialParameterR2dbcRepository financialRepo;
    private final MaintenanceParameterR2dbcRepository maintenanceRepo;
    private final VehicleIllustrationImageR2dbcRepository galleryRepo;
    private final DriverR2dbcRepository driverRepo;
    private final VehicleLocalMapper mapper;
    private final OperationalParameterR2dbcRepository operationalRepo;

    @Override
    @Transactional
    public Mono<Vehicle> saveLocalData(Vehicle vehicle) {
        return vehicleRepo.findById(vehicle.id())
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMap(existing -> {
                    VehicleLocalEntity vEntity = mapper.toVehicleEntity(vehicle);
                    vEntity.setLicensePlate(com.yowyob.fleet.domain.model.VehicleLifecycle.normalizePlate(vEntity.getLicensePlate()));
                    existing.ifPresentOrElse(old -> {
                        // Le cycle de vie n'est écrit que par updateLifecycle : une sauvegarde
                        // ordinaire (lien chauffeur, photos, flotte…) le conserve tel quel.
                        vEntity.setStatusReason(old.getStatusReason());
                        vEntity.setStatusChangedAt(old.getStatusChangedAt());
                        vEntity.setRetiredAt(old.getRetiredAt());
                        vEntity.setRetirementType(old.getRetirementType());
                        vEntity.setRetirementReason(old.getRetirementReason());
                        vEntity.setSalePrice(old.getSalePrice());
                        vEntity.setCreatedAt(old.getCreatedAt());
                        if (com.yowyob.fleet.domain.model.VehicleLifecycle.RETIRED.equals(old.getStatus())) {
                            vEntity.setStatus(old.getStatus());
                        }
                        if (!java.util.Objects.equals(old.getStatus(), vEntity.getStatus())) {
                            vEntity.setStatusChangedAt(Instant.now());
                            vEntity.setStatusReason(null);
                        }
                    }, () -> vEntity.setStatusChangedAt(Instant.now()));
                    vEntity.setNew(existing.isEmpty());
                    return vehicleRepo.save(vEntity);
                })
                // Correction : On transforme l'erreur technique SQL en erreur métier explicite (409)
                .onErrorMap(DataIntegrityViolationException.class, e -> VehicleException.plateConflict())
                .flatMap(savedV -> saveParameters(vehicle, savedV.getId()))
                .flatMap(savedV -> getLocalDataById(savedV.getId()));
    }

    private Mono<VehicleLocalEntity> saveParameters(Vehicle vehicle, UUID vehicleId) {
        FinancialParameterEntity fin = mapper.toFinancialEntity(vehicle);
        if (fin.getId() == null) { 
            fin.setId(UUID.randomUUID()); 
            fin.setNew(true); 
        }
        fin.setVehicleId(vehicleId);

        MaintenanceParameterEntity maint = mapper.toMaintenanceEntity(vehicle);
        if (maint.getId() == null) { 
            maint.setId(UUID.randomUUID()); 
            maint.setNew(true); 
        }
        maint.setVehicleId(vehicleId);

        return Mono.zip(
                financialRepo.findByVehicleId(vehicleId)
                    .map(existing -> { fin.setId(existing.getId()); fin.setNew(false); return fin; })
                    .defaultIfEmpty(fin).flatMap(financialRepo::save),
                maintenanceRepo.findByVehicleId(vehicleId)
                    .map(existing -> { maint.setId(existing.getId()); maint.setNew(false); return maint; })
                    .defaultIfEmpty(maint).flatMap(maintenanceRepo::save)
        ).then(Mono.fromSupplier(() -> {
            VehicleLocalEntity stub = new VehicleLocalEntity();
            stub.setId(vehicleId);
            stub.setNew(false);
            return stub;
        }));
    }

    @Override
    public Mono<Vehicle> getLocalDataById(UUID id) {
        return Mono.zip(
                vehicleRepo.findById(id),
                financialRepo.findByVehicleId(id).defaultIfEmpty(new FinancialParameterEntity()),
                maintenanceRepo.findByVehicleId(id).defaultIfEmpty(new MaintenanceParameterEntity()),
                galleryRepo.findByVehicleId(id).map(VehicleIllustrationImageEntity::getImagePath).collectList().defaultIfEmpty(List.of()),
                operationalRepo.findByVehicleId(id).defaultIfEmpty(new OperationalParameterEntity())
        ).map(tuple -> mapper.toDomain(tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4(), tuple.getT5()));
    }

    @Override
    public Mono<Vehicle> updateLifecycle(UUID id, String status, String statusReason, java.time.LocalDate retiredAt,
                                         String retirementType, String retirementReason, java.math.BigDecimal salePrice) {
        return vehicleRepo.findById(id)
                .switchIfEmpty(Mono.error(VehicleException.notFound(id)))
                .flatMap(entity -> {
                    entity.setStatus(status);
                    entity.setStatusReason(statusReason);
                    entity.setStatusChangedAt(Instant.now());
                    entity.setRetiredAt(retiredAt);
                    entity.setRetirementType(retirementType);
                    entity.setRetirementReason(retirementReason);
                    entity.setSalePrice(salePrice);
                    entity.setNew(false);
                    return vehicleRepo.save(entity);
                })
                .then(getLocalDataById(id));
    }

    @Override
    public Mono<Void> deleteLocalData(UUID id) {
        return vehicleRepo.findById(id)
                .flatMap(entity -> {
                    if (entity.getDeletedAt() != null) {
                        return Mono.just(entity);
                    }
                    entity.setDeletedAt(Instant.now());
                    entity.setCurrentDriverId(null);
                    entity.setNew(false);
                    return vehicleRepo.save(entity);
                })
                .then(driverRepo.findByAssignedVehicleId(id)
                        .flatMap(driver -> {
                            driver.setAssignedVehicleId(null);
                            driver.markAsExisting();
                            return driverRepo.save(driver);
                        })
                        .then());
    }

    @Override
    public Flux<Vehicle> getVehiclesByManager(UUID managerId) {
        return vehicleRepo.findAllInAssignedFleets(managerId)
                .flatMap(v -> getLocalDataById(v.getId()));
    }

    @Override
    public Flux<Vehicle> getVehiclesByOrganizationAdminId(UUID adminId) {
        return vehicleRepo.findAllByOrganizationAdminId(adminId)
                .flatMap(v -> getLocalDataById(v.getId()));
    }

    @Override
    public Flux<Vehicle> getAllVehicles() {
        return vehicleRepo.findAll()
                .filter(v -> v.getDeletedAt() == null)
                .flatMap(v -> getLocalDataById(v.getId()));
    }

    @Override
    public Mono<Void> updateVehiclePhotos(UUID vehicleId, String photoUrl, String vinPhotoUrl, String regPhotoUrl) {
        return vehicleRepo.findById(vehicleId)
                .flatMap(v -> {
                    if (photoUrl != null) v.setPhotoUrl(photoUrl);
                    if (vinPhotoUrl != null) v.setSerialNumberPhotoUrl(vinPhotoUrl);
                    if (regPhotoUrl != null) v.setRegistrationPhotoUrl(regPhotoUrl);
                    v.setNew(false);
                    return vehicleRepo.save(v);
                }).then();
    }
}