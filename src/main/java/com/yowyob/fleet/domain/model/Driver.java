package com.yowyob.fleet.domain.model;

import java.util.UUID;

public record Driver(
    UUID userId,
    UUID fleetId,
    String licenceNumber,
    String status,
    UUID assignedVehicleId,
    String photoUrl,
    UUID kernelActorId
) {}