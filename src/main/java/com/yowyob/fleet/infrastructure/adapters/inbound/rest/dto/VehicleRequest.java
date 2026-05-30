package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record VehicleRequest(
    @NotNull(message = "Le type de véhicule est obligatoire")
    @Schema(description = "ID issu de /lookup/vehicle-types")
    UUID vehicleTypeId,

    @NotNull(message = "Le constructeur est obligatoire")
    @Schema(description = "ID issu de /lookup/manufacturers")
    UUID manufacturerId,

    @NotNull(message = "La marque est obligatoire")
    @Schema(description = "ID issu de /lookup/brands")
    UUID brandId,

    @NotNull(message = "Le modèle est obligatoire")
    @Schema(description = "ID issu de /lookup/models")
    UUID modelId,

    @NotNull(message = "Le gabarit est obligatoire")
    @Schema(description = "ID issu de /lookup/sizes")
    UUID sizeId,

    @NotNull(message = "Le type d'usage est obligatoire")
    @Schema(description = "ID issu de /lookup/usages")
    UUID usageTypeId,

    @NotNull(message = "L'énergie est obligatoire")
    @Schema(description = "ID issu de /lookup/fuel-types")
    UUID fuelTypeId,

    @NotNull(message = "La transmission est obligatoire")
    @Schema(description = "ID issu de /lookup/transmissions")
    UUID transmissionTypeId,

    @NotNull(message = "La couleur est obligatoire")
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
    String registrationPhotoUrl
) {}