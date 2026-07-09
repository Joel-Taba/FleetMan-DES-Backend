package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.model.VehicleParameters;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.UUID;

public interface ManageVehicleUseCase {
    
    Mono<Void> assignVehicleToFleet(UUID fleetId, UUID vehicleId, UUID managerId);
    // --- 09a. Gestion du Parc (Fleet Manager) ---
    Mono<Vehicle> getVehicleDetails(UUID vehicleId, String token);
    Flux<Vehicle> getVehicles(UUID requesterId, boolean isAdmin, String token); 
    Mono<Vehicle> createIndependentVehicle(VehicleRequest request, UUID managerId, String token);
    Mono<Vehicle> createVehicle(UUID fleetId, VehicleRequest request, UUID managerId, String token); // Conserver pour compatibilité
    Mono<Vehicle> patchVehicleInfo(UUID vehicleId, Map<String, Object> updates, String token);
    Mono<Vehicle> updateVehicleGallery(UUID vehicleId, String photoUrl, java.util.List<String> galleryUrls, String token);
    Mono<Vehicle> updateFinancialParameters(UUID vehicleId, VehicleParameters.Financial params, String token);
    Mono<Vehicle> updateMaintenanceParameters(UUID vehicleId, VehicleParameters.Maintenance params, String token);
    Mono<Void> removeVehicle(UUID vehicleId, String token);

    default Flux<Vehicle> getVehicles(UUID requesterId, boolean isAdmin, String token, UUID fleetId) {
        Flux<Vehicle> base = getVehicles(requesterId, isAdmin, token);
        if (fleetId == null) return base;
        return base.filter(v -> fleetId.equals(v.fleetId()));
    }

    // --- 09c. Opérationnel (Driver) ---
    Mono<VehicleParameters.Operational> getOperationalData(UUID vehicleId);
    Mono<Void> updateOperationalData(UUID vehicleId, Map<String, Object> updates);

    // --- 09d. Référentiels (Lookup Manager/Public) ---
    Flux<Map<String, Object>> getLocalLookupData(String resource);
    Mono<Map<String, Object>> getAllResourcesCatalog();
}