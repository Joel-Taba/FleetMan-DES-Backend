package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.Vehicle;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface ManageVehicleMediaUseCase {
    // --- Documents Officiels (1-1) ---
    Mono<Vehicle> uploadVinPhoto(UUID vehicleId, FilePart file, String token);
    Mono<Vehicle> deleteVinPhoto(UUID vehicleId, String token);
    
    Mono<Vehicle> uploadRegistrationPhoto(UUID vehicleId, FilePart file, String token);
    Mono<Vehicle> deleteRegistrationPhoto(UUID vehicleId, String token);
    
    // --- Galerie d'Illustration (1-N) ---
    Mono<Vehicle> addIllustrationImage(UUID vehicleId, FilePart file, String token);
    Mono<Vehicle> deleteIllustrationImage(UUID vehicleId, UUID imageId, String token);
}