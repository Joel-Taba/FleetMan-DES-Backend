package com.yowyob.fleet.domain.model;

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
    
    // Galerie (1-N)
    List<String> illustrationImages,
    
    // Sous-objets
    VehicleParameters.Financial financialParameters,
    VehicleParameters.Maintenance maintenanceParameters,
    VehicleParameters.Operational operationalParameters,
    String geofenceRemoteId,
    UUID kernelResourceId
) {
    // Helper pour ajouter des images de galerie
    public Vehicle withGallery(List<String> images) {
        return new Vehicle(id, fleetId, managerId, currentDriverId, vehicleTypeId, licensePlate, 
            vehicleSerialNumber, brand, model, manufacturingYear, transmissionType, fuelType, 
            tankCapacity, totalSeatNumber, averageFuelConsumption, color, status, photoUrl, 
            serialNumberPhotoUrl, registrationPhotoUrl, images, financialParameters, 
            maintenanceParameters, operationalParameters, geofenceRemoteId, kernelResourceId);
    }
    public Vehicle withGeofenceRemoteId(String newRemoteId) {
        return new Vehicle(id, fleetId, managerId, currentDriverId, vehicleTypeId, licensePlate, 
            vehicleSerialNumber, brand, model, manufacturingYear, transmissionType, fuelType, 
            tankCapacity, totalSeatNumber, averageFuelConsumption, color, status, photoUrl, 
            serialNumberPhotoUrl, registrationPhotoUrl, illustrationImages, financialParameters, 
            maintenanceParameters, operationalParameters, newRemoteId, kernelResourceId);
    }
    public Vehicle withKernelResourceId(UUID kernelResourceId) {
        return new Vehicle(id, fleetId, managerId, currentDriverId, vehicleTypeId, licensePlate,
            vehicleSerialNumber, brand, model, manufacturingYear, transmissionType, fuelType,
            tankCapacity, totalSeatNumber, averageFuelConsumption, color, status, photoUrl,
            serialNumberPhotoUrl, registrationPhotoUrl, illustrationImages, financialParameters,
            maintenanceParameters, operationalParameters, geofenceRemoteId, kernelResourceId);
    }
}