package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.ports.in.ManageVehicleMediaUseCase;
import com.yowyob.fleet.domain.ports.in.ManageVehicleUseCase;
import com.yowyob.fleet.domain.ports.out.ExternalVehiclePort;
import com.yowyob.fleet.domain.ports.out.VehiclePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleIllustrationImageEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleIllustrationImageR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleMediaService implements ManageVehicleMediaUseCase {

    private final ExternalVehiclePort externalVehiclePort;
    private final VehiclePersistencePort localPersistencePort;
    private final VehicleIllustrationImageR2dbcRepository galleryRepo;
    private final ManageVehicleUseCase vehicleUseCase; // Pour récupérer l'objet complet après update

    @Override
    @Transactional
    public Mono<Vehicle> uploadVinPhoto(UUID vehicleId, FilePart file, String token) {
        return externalVehiclePort.uploadDocument(vehicleId, "serial", file, token)
                .then(externalVehiclePort.getExternalVehicleInfo(vehicleId, token))
                .flatMap(remote -> localPersistencePort.updateVehiclePhotos(
                        vehicleId, null, remote.serialNumberPhotoUrl(), null))
                .then(vehicleUseCase.getVehicleDetails(vehicleId, token));
    }

    @Override
    @Transactional
    public Mono<Vehicle> deleteVinPhoto(UUID vehicleId, String token) {
        return externalVehiclePort.deleteDocument(vehicleId, "serial", token)
                .then(localPersistencePort.updateVehiclePhotos(vehicleId, null, "", null))
                .then(vehicleUseCase.getVehicleDetails(vehicleId, token));
    }

    @Override
    @Transactional
    public Mono<Vehicle> uploadRegistrationPhoto(UUID vehicleId, FilePart file, String token) {
        return externalVehiclePort.uploadDocument(vehicleId, "registration", file, token)
                .then(externalVehiclePort.getExternalVehicleInfo(vehicleId, token))
                .flatMap(remote -> localPersistencePort.updateVehiclePhotos(
                        vehicleId, null, null, remote.registrationPhotoUrl()))
                .then(vehicleUseCase.getVehicleDetails(vehicleId, token));
    }

    @Override
    @Transactional
    public Mono<Vehicle> deleteRegistrationPhoto(UUID vehicleId, String token) {
        return externalVehiclePort.deleteDocument(vehicleId, "registration", token)
                .then(localPersistencePort.updateVehiclePhotos(vehicleId, null, null, ""))
                .then(vehicleUseCase.getVehicleDetails(vehicleId, token));
    }

    @Override
    @Transactional
    public Mono<Vehicle> addIllustrationImage(UUID vehicleId, FilePart file, String token) {
        return externalVehiclePort.addImage(vehicleId, file, token)
                .flatMap(url -> galleryRepo.save(new VehicleIllustrationImageEntity(UUID.randomUUID(), vehicleId, url, true)))
                .then(vehicleUseCase.getVehicleDetails(vehicleId, token));
    }

    @Override
    @Transactional
    public Mono<Vehicle> deleteIllustrationImage(UUID vehicleId, UUID imageId, String token) {
        // 1. Trouver l'URL pour la supprimer en externe (si besoin)
        return galleryRepo.findById(imageId)
                .flatMap(entity -> externalVehiclePort.deleteImage(entity.getImagePath(), token)
                        .onErrorResume(e -> Mono.empty()) // On continue même si le distant échoue
                        .then(galleryRepo.deleteById(imageId)))
                .then(vehicleUseCase.getVehicleDetails(vehicleId, token));
    }
}