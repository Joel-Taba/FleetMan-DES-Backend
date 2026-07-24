package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FleetResponse(
    UUID id,
    String name,
    String phoneNumber,
    BigDecimal monthlyBudget,
    LocalDate creationDate,
    UUID managerUserId,
    Integer vehicleCount
) {}