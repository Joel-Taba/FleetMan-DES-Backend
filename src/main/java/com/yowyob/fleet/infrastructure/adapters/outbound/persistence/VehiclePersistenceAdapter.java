package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.exception.VehicleException;
import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.ports.out.VehiclePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.*;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.*;
import com.yowyob.fleet.infrastructure.mappers.VehicleLocalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VehiclePersistenceAdapter implements VehiclePersistencePort {

    private final VehicleLocalR2dbcRepository vehicleRepo;
    private final FinancialParameterR2dbcRepository financialRepo;
    private final MaintenanceParameterR2dbcRepository maintenanceRepo;
    private final VehicleIllustrationImageR2dbcRepository galleryRepo;
    private final VehicleLocalMapper mapper;

    @Override
    @Transactional
    public Mono<Vehicle> saveLocalData(Vehicle vehicle) {
        return vehicleRepo.existsById(vehicle.id())
                .flatMap(exists -> {
                    VehicleLocalEntity vEntity = mapper.toVehicleEntity(vehicle);
                    vEntity.setNew(!exists); 
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
        ).thenReturn(new VehicleLocalEntity(vehicleId, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false));
    }

    @Override
    public Mono<Vehicle> getLocalDataById(UUID id) {
        return Mono.zip(
                vehicleRepo.findById(id),
                financialRepo.findByVehicleId(id).defaultIfEmpty(new FinancialParameterEntity()),
                maintenanceRepo.findByVehicleId(id).defaultIfEmpty(new MaintenanceParameterEntity()),
                galleryRepo.findByVehicleId(id).map(VehicleIllustrationImageEntity::getImagePath).collectList().defaultIfEmpty(List.of())
        ).map(tuple -> mapper.toDomain(tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4()));
    }

    @Override
    public Mono<Void> deleteLocalData(UUID id) {
        return vehicleRepo.deleteById(id);
    }

    @Override
    public Flux<Vehicle> getVehiclesByManager(UUID managerId) {
        return vehicleRepo.findByManagerId(managerId).flatMap(v -> getLocalDataById(v.getId()));
    }

    @Override
    public Flux<Vehicle> getAllVehicles() {
        return vehicleRepo.findAll().flatMap(v -> getLocalDataById(v.getId()));
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