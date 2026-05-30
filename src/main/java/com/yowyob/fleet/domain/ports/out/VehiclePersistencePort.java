package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.Vehicle;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux; // Import Flux
import java.util.UUID;

public interface VehiclePersistencePort {
    /**
     * Saves the local data of a vehicle (Pivot, Financial, and Maintenance).
     */
    Mono<Vehicle> saveLocalData(Vehicle vehicle);

    /**
     * Retrieves only the local data stored in our database for a vehicle.
     */
    Mono<Vehicle> getLocalDataById(UUID id);

    /**
     * Deletes all local records associated with a vehicle ID.
     */
    Mono<Void> deleteLocalData(UUID id);

    /**
     * Retrieves all vehicles associated with a manager ID.
     */
    Flux<Vehicle> getVehiclesByManager(UUID managerId);

    /**
     * Retrieves ALL vehicles in the system (For Admin).
     */
    Flux<Vehicle> getAllVehicles();

    /**
     * Updates the photo URLs for a vehicle.
     */
    Mono<Void> updateVehiclePhotos(UUID vehicleId, String photoUrl, String vinPhotoUrl, String regPhotoUrl);
}