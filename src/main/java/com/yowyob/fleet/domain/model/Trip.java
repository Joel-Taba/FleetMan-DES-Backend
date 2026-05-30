package com.yowyob.fleet.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record Trip(
    UUID id,
    UUID vehicleId,
    UUID driverId,
    String status,          // SCHEDULED, ONGOING, COMPLETED, CANCELLED
    LocalDate startDate,
    LocalTime startTime,
    LocalDate endDate,
    LocalTime endTime,
    Double distanceKm,
    Integer durationMinutes
) {}