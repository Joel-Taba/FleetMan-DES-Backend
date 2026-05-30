package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.time.LocalDate;
import java.util.UUID;

public record FleetResponse(
    UUID id,
    String name,
    LocalDate creationDate,
    UUID managerUserId,
    Integer vehicleCount
) {}