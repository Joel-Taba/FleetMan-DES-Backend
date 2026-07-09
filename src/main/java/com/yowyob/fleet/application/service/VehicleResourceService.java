package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.VehicleException;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.resources.ResourceRequest;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.resources.*;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.resources.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleResourceService {

    private final ManufacturerR2dbcRepository mfrRepo;
    private final BrandR2dbcRepository brandRepo;
    private final VehicleModelR2dbcRepository modelRepo;
    private final VehicleSizeR2dbcRepository sizeRepo;
    private final UsageTypeR2dbcRepository usageRepo;
    private final FuelTypeR2dbcRepository fuelRepo;
    private final TransmissionTypeR2dbcRepository transRepo;
    private final VehicleColorR2dbcRepository colorRepo;

    // --- LOGIQUE GÉNÉRIQUE (Pour éviter la répétition) ---

    private <T> Mono<T> create(ReactiveCrudRepository<T, UUID> repo, ResourceRequest req, java.util.function.Function<UUID, T> creator) {
        if (req.normalizedCode() == null || req.normalizedLabel() == null) {
            return Mono.error(VehicleException.invalidResource());
        }
        T entity = creator.apply(UUID.randomUUID());
        return repo.save(entity).onErrorMap(DuplicateKeyException.class, ex -> VehicleException.duplicateResourceCode());
    }

    private <T> Mono<T> update(ReactiveCrudRepository<T, UUID> repo, UUID id, ResourceRequest req, java.util.function.BiConsumer<T, ResourceRequest> updater) {
        if (req.normalizedCode() == null || req.normalizedLabel() == null) {
            return Mono.error(VehicleException.invalidResource());
        }
        return repo.findById(id)
                .switchIfEmpty(Mono.error(VehicleException.invalidResource()))
                .flatMap(existing -> {
                    updater.accept(existing, req);
                    return repo.save(existing);
                }).onErrorMap(DuplicateKeyException.class, ex -> VehicleException.duplicateResourceCode());
    }

    // --- 1. MANUFACTURERS ---
    public Flux<ManufacturerEntity> getAllMfr() { return mfrRepo.findAll(); }
    public Mono<ManufacturerEntity> getMfr(UUID id) { return mfrRepo.findById(id); }
    public Mono<ManufacturerEntity> createMfr(ResourceRequest r) { return create(mfrRepo, r, id -> new ManufacturerEntity(id, r.normalizedCode(), r.normalizedLabel(), r.description(), true)); }
    public Mono<ManufacturerEntity> updateMfr(UUID id, ResourceRequest r) { return update(mfrRepo, id, r, (e, req) -> { e.setCode(req.normalizedCode()); e.setLabel(req.normalizedLabel()); e.setDescription(req.description()); }); }
    public Mono<Void> deleteMfr(UUID id) { return mfrRepo.deleteById(id); }

    // --- 2. BRANDS ---
    public Flux<BrandEntity> getAllBrd() { return brandRepo.findAll(); }
    public Mono<BrandEntity> getBrd(UUID id) { return brandRepo.findById(id); }
    public Mono<BrandEntity> createBrd(ResourceRequest r) { return create(brandRepo, r, id -> new BrandEntity(id, r.normalizedCode(), r.normalizedLabel(), r.description(), true)); }
    public Mono<BrandEntity> updateBrd(UUID id, ResourceRequest r) { return update(brandRepo, id, r, (e, req) -> { e.setCode(req.normalizedCode()); e.setLabel(req.normalizedLabel()); e.setDescription(req.description()); }); }
    public Mono<Void> deleteBrd(UUID id) { return brandRepo.deleteById(id); }

    // --- 3. MODELS ---
    public Flux<VehicleModelEntity> getAllMod() { return modelRepo.findAll(); }
    public Mono<VehicleModelEntity> getMod(UUID id) { return modelRepo.findById(id); }
    public Mono<VehicleModelEntity> createMod(ResourceRequest r) { return create(modelRepo, r, id -> new VehicleModelEntity(id, r.normalizedCode(), r.normalizedLabel(), r.description(), true)); }
    public Mono<VehicleModelEntity> updateMod(UUID id, ResourceRequest r) { return update(modelRepo, id, r, (e, req) -> { e.setCode(req.normalizedCode()); e.setLabel(req.normalizedLabel()); e.setDescription(req.description()); }); }
    public Mono<Void> deleteMod(UUID id) { return modelRepo.deleteById(id); }

    // --- 4. SIZES ---
    public Flux<VehicleSizeEntity> getAllSize() { return sizeRepo.findAll(); }
    public Mono<VehicleSizeEntity> getSize(UUID id) { return sizeRepo.findById(id); }
    public Mono<VehicleSizeEntity> createSize(ResourceRequest r) { return create(sizeRepo, r, id -> new VehicleSizeEntity(id, r.normalizedCode(), r.normalizedLabel(), r.description(), true)); }
    public Mono<VehicleSizeEntity> updateSize(UUID id, ResourceRequest r) { return update(sizeRepo, id, r, (e, req) -> { e.setCode(req.normalizedCode()); e.setLabel(req.normalizedLabel()); e.setDescription(req.description()); }); }
    public Mono<Void> deleteSize(UUID id) { return sizeRepo.deleteById(id); }

    // --- 5. USAGES ---
    public Flux<UsageTypeEntity> getAllUsage() { return usageRepo.findAll(); }
    public Mono<UsageTypeEntity> getUsage(UUID id) { return usageRepo.findById(id); }
    public Mono<UsageTypeEntity> createUsage(ResourceRequest r) { return create(usageRepo, r, id -> new UsageTypeEntity(id, r.normalizedCode(), r.normalizedLabel(), r.description(), true)); }
    public Mono<UsageTypeEntity> updateUsage(UUID id, ResourceRequest r) { return update(usageRepo, id, r, (e, req) -> { e.setCode(req.normalizedCode()); e.setLabel(req.normalizedLabel()); e.setDescription(req.description()); }); }
    public Mono<Void> deleteUsage(UUID id) { return usageRepo.deleteById(id); }

    // --- 6. FUELS ---
    public Flux<FuelTypeEntity> getAllFuel() { return fuelRepo.findAll(); }
    public Mono<FuelTypeEntity> getFuel(UUID id) { return fuelRepo.findById(id); }
    public Mono<FuelTypeEntity> createFuel(ResourceRequest r) { return create(fuelRepo, r, id -> new FuelTypeEntity(id, r.normalizedCode(), r.normalizedLabel(), r.description(), true)); }
    public Mono<FuelTypeEntity> updateFuel(UUID id, ResourceRequest r) { return update(fuelRepo, id, r, (e, req) -> { e.setCode(req.normalizedCode()); e.setLabel(req.normalizedLabel()); e.setDescription(req.description()); }); }
    public Mono<Void> deleteFuel(UUID id) { return fuelRepo.deleteById(id); }

    // --- 7. TRANSMISSIONS ---
    public Flux<TransmissionTypeEntity> getAllTrans() { return transRepo.findAll(); }
    public Mono<TransmissionTypeEntity> getTrans(UUID id) { return transRepo.findById(id); }
    public Mono<TransmissionTypeEntity> createTrans(ResourceRequest r) { return create(transRepo, r, id -> new TransmissionTypeEntity(id, r.normalizedCode(), r.normalizedLabel(), r.description(), true)); }
    public Mono<TransmissionTypeEntity> updateTrans(UUID id, ResourceRequest r) { return update(transRepo, id, r, (e, req) -> { e.setCode(req.normalizedCode()); e.setLabel(req.normalizedLabel()); e.setDescription(req.description()); }); }
    public Mono<Void> deleteTrans(UUID id) { return transRepo.deleteById(id); }

    // --- 8. COLORS ---
    public Flux<VehicleColorEntity> getAllColor() { return colorRepo.findAll(); }
    public Mono<VehicleColorEntity> getColor(UUID id) { return colorRepo.findById(id); }
    public Mono<VehicleColorEntity> createColor(ResourceRequest r) { return create(colorRepo, r, id -> new VehicleColorEntity(id, r.normalizedCode(), r.normalizedLabel(), r.description(), true)); }
    public Mono<VehicleColorEntity> updateColor(UUID id, ResourceRequest r) { return update(colorRepo, id, r, (e, req) -> { e.setCode(req.normalizedCode()); e.setLabel(req.normalizedLabel()); e.setDescription(req.description()); }); }
    public Mono<Void> deleteColor(UUID id) { return colorRepo.deleteById(id); }
}