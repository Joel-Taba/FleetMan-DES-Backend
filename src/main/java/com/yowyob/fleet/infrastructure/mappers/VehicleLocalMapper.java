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
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "statusReason", ignore = true)
    @Mapping(target = "statusChangedAt", ignore = true)
    @Mapping(target = "retiredAt", ignore = true)
    @Mapping(target = "retirementType", ignore = true)
    @Mapping(target = "retirementReason", ignore = true)
    @Mapping(target = "salePrice", ignore = true)
    public abstract VehicleLocalEntity toVehicleEntity(Vehicle domain);

    public Vehicle toDomain(VehicleLocalEntity v, FinancialParameterEntity f, MaintenanceParameterEntity m, List<String> gallery) {
        return toDomain(v, f, m, gallery, null);
    }

    public Vehicle toDomain(VehicleLocalEntity v, FinancialParameterEntity f, MaintenanceParameterEntity m,
                            List<String> gallery, OperationalParameterEntity o) {
        if (v == null) return null;

        return new Vehicle(
            v.getId(),
            v.getFleetId(),
            v.getManagerId(),
            v.getCurrentDriverId(),
            v.getVehicleTypeId(),
            v.getLicensePlate(),
            v.getVehicleSerialNumber(),
            v.getBrand(),
            v.getModel(),
            v.getManufacturingYear(),
            v.getTransmissionType(),
            v.getFuelType(),
            v.getTankCapacity() != null ? v.getTankCapacity().doubleValue() : null,
            v.getTotalSeatNumber(),
            v.getAverageFuelConsumption() != null ? v.getAverageFuelConsumption().doubleValue() : null,
            v.getColor(),
            v.getStatus(),
            v.getPhotoUrl(),
            v.getSerialNumberPhotoUrl(),
            v.getRegistrationPhotoUrl(),
            gallery,
            mapFinancialToDomain(f),
            mapMaintenanceToDomain(m),
            mapOperationalToDomain(o),
            v.getGeofenceRemoteId(),
            v.getKernelResourceId(),
            new com.yowyob.fleet.domain.model.VehicleParameters.Lifecycle(
                v.getStatusReason(), v.getStatusChangedAt(), v.getRetiredAt(),
                v.getRetirementType(), v.getRetirementReason(), v.getSalePrice())
        );
    }

    protected com.yowyob.fleet.domain.model.VehicleParameters.Operational mapOperationalToDomain(OperationalParameterEntity o) {
        if (o == null || o.getVehicleId() == null) return null;
        return new com.yowyob.fleet.domain.model.VehicleParameters.Operational(
            o.getStatut(),
            o.getCurrentSpeed() != null ? o.getCurrentSpeed().floatValue() : null,
            o.getFuelLevel(),
            o.getOdometerReading() != null ? o.getOdometerReading().floatValue() : null,
            o.getOdometerReading() != null ? o.getOdometerReading().floatValue() : null,
            o.getBearing() != null ? o.getBearing().floatValue() : null,
            o.getTimestamp(),
            null
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