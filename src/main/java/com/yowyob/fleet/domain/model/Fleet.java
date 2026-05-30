package com.yowyob.fleet.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Fleet(
    UUID id,
    UUID managerId,
    String name,
    String phoneNumber,
    Instant createdAt,
    Integer vehicleCount
) {}