package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.util.UUID;

public record FleetManagerResponse(
    UUID userId,
    String firstName,
    String lastName,
    String email,
    String phone,
    String companyName,
    String status,
    Integer fleetCount,
    String photoUrl
) {}