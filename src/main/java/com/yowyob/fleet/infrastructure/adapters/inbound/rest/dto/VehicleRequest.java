package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Payload véhicule — compatible:
 * - flux Kernel (IDs référentiels UUID)
 * - flux Manager front (fleetId + brand/model strings)
 */
public record VehicleRequest(
    @Schema(description = "Flotte cible (requis côté Manager UI)")
    UUID fleetId,

    @Schema(description = "ID issu de /lookup/vehicle-types")
    UUID vehicleTypeId,

    @Schema(description = "ID issu de /lookup/manufacturers")
    UUID manufacturerId,

    @Schema(description = "ID issu de /lookup/brands")
    UUID brandId,

    @Schema(description = "ID issu de /lookup/models")
    UUID modelId,

    @Schema(description = "ID issu de /lookup/sizes")
    UUID sizeId,

    @Schema(description = "ID issu de /lookup/usages")
    UUID usageTypeId,

    @Schema(description = "ID issu de /lookup/fuel-types")
    UUID fuelTypeId,

    @Schema(description = "ID issu de /lookup/transmissions")
    UUID transmissionTypeId,

    @Schema(description = "ID issu de /lookup/colors")
    UUID colorId,

    @NotBlank(message = "La plaque d'immatriculation est obligatoire")
    @Schema(example = "LT-123-AA")
    String licensePlate,

    @Schema(example = "VIN-987654321")
    String vehicleSerialNumber,

    @Schema(example = "2024")
    Integer manufacturingYear,

    Double tankCapacity,
    Integer totalSeatNumber,
    Double averageFuelConsumption,

    String photoUrl,
    String serialNumberPhotoUrl,
    String registrationPhotoUrl,

    /** Labels front (prioritaires si brandId/modelId absents). */
    String brand,
    String model,
    String fuelType,
    String transmissionType,
    String color,
    String status
) {}
