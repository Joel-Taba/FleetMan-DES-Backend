package com.yowyob.fleet.infrastructure.mappers;

import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class VehicleLocalMapper {

    @Mapping(target = "new", ignore = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "geofenceRemoteId", source = "geofenceRemoteId")
    @Mapping(target = "kernelResourceId", source = "kernelResourceId")
    public abstract VehicleLocalEntity toVehicleEntity(Vehicle domain);

    public Vehicle toDomain(VehicleLocalEntity v, FinancialParameterEntity f, MaintenanceParameterEntity m, List<String> gallery) {
        if (v == null) return null;

        return new Vehicle(
            v.getId(),
            v.getFleetId(),
            v.getManagerId(),
            v.getCurrentDriverId(),
            v.getVehicleTypeId(),
            v.getLicensePlate(),
            null, 
            v.getBrand(),
            v.getModel(),
            v.getManufacturingYear(),
            null, 
            null, 
            null, 
            null, 
            null, 
            v.getColor(),
            v.getStatus(),
            v.getPhotoUrl(),
            v.getSerialNumberPhotoUrl(),
            v.getRegistrationPhotoUrl(),
            gallery, 
            mapFinancialToDomain(f),
            mapMaintenanceToDomain(m),
            null,
            v.getGeofenceRemoteId(),
            v.getKernelResourceId()
        );
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "new", ignore = true)
    @Mapping(target = "vehicleId", source = "id")
    @Mapping(target = "insuranceNumber", source = "financialParameters.insuranceNumber")
    @Mapping(target = "insuranceExpiredAt", source = "financialParameters.insuranceExpiryDate")
    @Mapping(target = "registeredAt", source = "financialParameters.registrationDate")
    @Mapping(target = "purchasedAt", source = "financialParameters.purchaseDate")
    @Mapping(target = "depreciationRate", source = "financialParameters.depreciationRate")
    @Mapping(target = "costPerKm", source = "financialParameters.costPerKm")
    public abstract FinancialParameterEntity toFinancialEntity(Vehicle domain);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "new", ignore = true)
    @Mapping(target = "vehicleId", source = "id")
    @Mapping(target = "lastMaintenanceAt", source = "maintenanceParameters.lastMaintenanceDate")
    @Mapping(target = "nextMaintenanceAt", source = "maintenanceParameters.nextMaintenanceDue")
    @Mapping(target = "engineStatus", source = "maintenanceParameters.engineStatus")
    @Mapping(target = "batteryHealth", source = "maintenanceParameters.batteryHealth")
    @Mapping(target = "maintenanceStatus", source = "maintenanceParameters.maintenanceStatus")
    public abstract MaintenanceParameterEntity toMaintenanceEntity(Vehicle domain);

    protected com.yowyob.fleet.domain.model.VehicleParameters.Financial mapFinancialToDomain(FinancialParameterEntity f) {
        if (f == null || f.getVehicleId() == null) return null;
        return new com.yowyob.fleet.domain.model.VehicleParameters.Financial(
            f.getInsuranceNumber(),
            f.getInsuranceExpiredAt(),
            f.getRegisteredAt(), 
            f.getPurchasedAt(),
            f.getDepreciationRate(),
            f.getCostPerKm()
        );
    }

    protected com.yowyob.fleet.domain.model.VehicleParameters.Maintenance mapMaintenanceToDomain(MaintenanceParameterEntity m) {
        if (m == null || m.getVehicleId() == null) return null;
        return new com.yowyob.fleet.domain.model.VehicleParameters.Maintenance(
            m.getLastMaintenanceAt(),
            m.getNextMaintenanceAt(),
            m.getEngineStatus(),
            m.getBatteryHealth(), 
            m.getMaintenanceStatus()
        );
    }
}