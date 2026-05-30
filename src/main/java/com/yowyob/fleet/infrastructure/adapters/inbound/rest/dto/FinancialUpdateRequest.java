package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.time.LocalDate;

public record FinancialUpdateRequest(
    String insuranceNumber,
    LocalDate insuranceExpiryDate,
    LocalDate registrationDate,
    LocalDate purchaseDate,
    Integer depreciationRate,
    Float costPerKm
) {}