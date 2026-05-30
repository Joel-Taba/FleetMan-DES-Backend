package com.yowyob.fleet.domain.model;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record GeofenceZone(
    UUID id,
    UUID fleetId,
    UUID managerId,
    String name,
    String description,
    String zoneType, // CIRCLE or POLYGON
    Double centerLatitude, // Pour CIRCLE
    Double centerLongitude, // Pour CIRCLE
    Double radius, // Pour CIRCLE
    Boolean isTemporalEnabled,
    LocalTime startTime,
    LocalTime endTime,
    List<String> activeDays, // MONDAY, TUESDAY...
    Boolean isConditionalEnabled,
    Double maxSpeed,
    Integer maxDwellTime,
    Integer minDwellTime,
    Boolean isActive,
    Double surfaceArea,
    Double perimeter,
    List<GeofencePoint> vertices // Pour POLYGON
) {



    public Boolean getIsActive() {
        return isActive;
    }

    public Boolean getIsTemporalEnabled() {
        return isTemporalEnabled;
    }

    public Boolean getIsConditionalEnabled() {
        return isConditionalEnabled;
    }
}