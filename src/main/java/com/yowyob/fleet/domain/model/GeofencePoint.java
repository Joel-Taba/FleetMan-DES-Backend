package com.yowyob.fleet.domain.model;

import java.util.UUID;

public record GeofencePoint(
    UUID id,
    Double latitude,
    Double longitude,
    Integer order
) {}