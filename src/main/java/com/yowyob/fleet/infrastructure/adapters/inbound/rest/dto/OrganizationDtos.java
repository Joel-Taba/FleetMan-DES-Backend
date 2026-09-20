package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class OrganizationDtos {

    private OrganizationDtos() {}

    public record OrganizationSummary(
            UUID adminId,
            long managerCount,
            long fleetCount,
            long vehicleCount,
            long availableVehicleCount,
            long driverCount,
            long tripCount,
            long incidentCount,
            long zoneCount
    ) {}

    public record OrganizationProfile(
            UUID adminId,
            String organizationName,
            String logoUrl,
            String firstName,
            String lastName,
            String email,
            String phone,
            String photoUrl,
            long managerCount,
            long fleetCount,
            long vehicleCount,
            long driverCount
    ) {}

    public record OrganizationVehicle(
            UUID id,
            UUID fleetId,
            UUID managerId,
            UUID currentDriverId,
            UUID vehicleTypeId,
            String vehicleTypeLabel,
            String licensePlate,
            String brand,
            String model,
            Integer manufacturingYear,
            String color,
            String status,
            String photoUrl,
            String geofenceRemoteId,
            String fuelLevel,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record OrganizationTrip(
            UUID id,
            String tripCode,
            UUID fleetId,
            UUID vehicleId,
            UUID driverId,
            UUID createdBy,
            String status,
            LocalDate startDate,
            LocalTime startTime,
            LocalDate endDate,
            LocalTime endTime,
            String departureLocation,
            String returnLocation,
            String missionObject,
            Double distanceKm,
            BigDecimal computedDistanceKm,
            String vehicleRegistration,
            String driverFullName,
            String cancelReason
    ) {}

    public record OrganizationIncident(
            UUID id,
            String type,
            String severity,
            String status,
            String description,
            LocalDateTime incidentDateTime,
            BigDecimal cost,
            UUID vehicleId,
            UUID driverId,
            String driverFullName,
            String vehicleRegistration,
            Double longitude,
            Double latitude,
            String reportedBy
    ) {}

    public record OrganizationZone(
            UUID id,
            UUID managerId,
            UUID fleetId,
            String zoneType
    ) {}

    public record OrganizationHistoryItem(
            String kind,
            UUID id,
            String title,
            String status,
            Instant occurredAt
    ) {}

    public record OrganizationKpis(
            OrganizationSummary counts,
            long completedTrips,
            long openIncidents,
            BigDecimal totalFuelCost,
            BigDecimal totalFuelLiters,
            long fuelRechargeCount,
            BigDecimal totalMaintenanceCost,
            long maintenanceCount,
            BigDecimal totalDistanceKm,
            long availableVehicles,
            long onTripVehicles,
            long maintenanceVehicles
    ) {}

    public record OrganizationFuelRecharge(
            UUID id,
            BigDecimal quantity,
            BigDecimal price,
            LocalDateTime rechargeDateTime,
            String stationName,
            UUID vehicleId,
            String vehicleRegistration,
            UUID driverId,
            String driverFullName
    ) {}

    public record OrganizationMaintenance(
            UUID id,
            String subject,
            BigDecimal cost,
            LocalDateTime dateTime,
            String locationName,
            UUID vehicleId,
            String vehicleRegistration,
            UUID driverId,
            String driverFullName
    ) {}

    public record OrganizationPeople(
            List<AuthManager> managers,
            List<DriverResponse> drivers
    ) {}

    @Schema(description = "Manager de l'organisation")
    public record AuthManager(
            UUID userId,
            String username,
            String email,
            String phone,
            String firstName,
            String lastName,
            String companyName,
            boolean isActive,
            UUID createdByAdminId
    ) {}
}
