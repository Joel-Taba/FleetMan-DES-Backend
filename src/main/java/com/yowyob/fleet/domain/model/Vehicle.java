package com.yowyob.fleet.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record Vehicle(
    UUID id,
    UUID fleetId,
    UUID managerId,
    UUID currentDriverId,
    UUID vehicleTypeId,
    
    // Identification
    String licensePlate,
    String vehicleSerialNumber,
    
    // Caractéristiques
    String brand,
    String model,
    Integer manufacturingYear,
    String transmissionType,
    String fuelType,
    Double tankCapacity,
    Integer totalSeatNumber,
    Double averageFuelConsumption,
    
    // Visuel
    String color,
    String status,
    
    // URLs stockées
    String photoUrl,
    String serialNumberPhotoUrl,
    String registrationPhotoUrl,
    
    // Galerie (1-N) — exposée comme galleryUrls pour le front Manager
    @JsonProperty("galleryUrls")
    List<String> illustrationImages,
    
    // Sous-objets
    VehicleParameters.Financial financialParameters,
    VehicleParameters.Maintenance maintenanceParameters,
    VehicleParameters.Operational operationalParameters,
    String geofenceRemoteId,
    UUID kernelResourceId,
    VehicleParameters.Lifecycle lifecycle
) {
    /** Constructeur historique (sans cycle de vie) : le cycle de vie n'est jamais écrasé à la sauvegarde. */
    public Vehicle(UUID id, UUID fleetId, UUID managerId, UUID currentDriverId, UUID vehicleTypeId,
                   String licensePlate, String vehicleSerialNumber, String brand, String model,
                   Integer manufacturingYear, String transmissionType, String fuelType, Double tankCapacity,
                   Integer totalSeatNumber, Double averageFuelConsumption, String color, String status,
                   String photoUrl, String serialNumberPhotoUrl, String registrationPhotoUrl,
                   List<String> illustrationImages, VehicleParameters.Financial financialParameters,
                   VehicleParameters.Maintenance maintenanceParameters,
                   VehicleParameters.Operational operationalParameters, String geofenceRemoteId,
                   UUID kernelResourceId) {
        this(id, fleetId, managerId, currentDriverId, vehicleTypeId, licensePlate, vehicleSerialNumber,
            brand, model, manufacturingYear, transmissionType, fuelType, tankCapacity, totalSeatNumber,
            averageFuelConsumption, color, status, photoUrl, serialNumberPhotoUrl, registrationPhotoUrl,
            illustrationImages, financialParameters, maintenanceParameters, operationalParameters,
            geofenceRemoteId, kernelResourceId, null);
    }

    // Helper pour ajouter des images de galerie
    public Vehicle withGallery(List<String> images) {
        return new Vehicle(id, fleetId, managerId, currentDriverId, vehicleTypeId, licensePlate, 
            vehicleSerialNumber, brand, model, manufacturingYear, transmissionType, fuelType, 
            tankCapacity, totalSeatNumber, averageFuelConsumption, color, status, photoUrl, 
            serialNumberPhotoUrl, registrationPhotoUrl, images, financialParameters, 
            maintenanceParameters, operationalParameters, geofenceRemoteId, kernelResourceId, lifecycle);
    }
    public Vehicle withGeofenceRemoteId(String newRemoteId) {
        return new Vehicle(id, fleetId, managerId, currentDriverId, vehicleTypeId, licensePlate, 
            vehicleSerialNumber, brand, model, manufacturingYear, transmissionType, fuelType, 
            tankCapacity, totalSeatNumber, averageFuelConsumption, color, status, photoUrl, 
            serialNumberPhotoUrl, registrationPhotoUrl, illustrationImages, financialParameters, 
            maintenanceParameters, operationalParameters, newRemoteId, kernelResourceId, lifecycle);
    }
    public Vehicle withKernelResourceId(UUID kernelResourceId) {
        return new Vehicle(id, fleetId, managerId, currentDriverId, vehicleTypeId, licensePlate,
            vehicleSerialNumber, brand, model, manufacturingYear, transmissionType, fuelType,
            tankCapacity, totalSeatNumber, averageFuelConsumption, color, status, photoUrl,
            serialNumberPhotoUrl, registrationPhotoUrl, illustrationImages, financialParameters,
            maintenanceParameters, operationalParameters, geofenceRemoteId, kernelResourceId, lifecycle);
    }
}