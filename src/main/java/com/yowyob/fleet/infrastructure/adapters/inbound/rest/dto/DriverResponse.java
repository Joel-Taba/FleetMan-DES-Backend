package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Conducteur enrichi avec identité utilisateur")
public record DriverResponse(
        UUID userId,
        UUID fleetId,
        UUID managerId,
        String firstName,
        String lastName,
        String email,
        String phone,
        String username,
        String licenceNumber,
        String status,
        UUID assignedVehicleId,
        String photoUrl
) {
    public static DriverResponse from(Driver driver, UserLocalEntity user) {
        return new DriverResponse(
                driver.userId(),
                driver.fleetId(),
                null,
                user != null ? user.getFirstName() : null,
                user != null ? user.getLastName() : null,
                user != null ? user.getEmail() : null,
                null,
                user != null ? user.getUsername() : null,
                driver.licenceNumber(),
                driver.status(),
                driver.assignedVehicleId(),
                driver.photoUrl()
        );
    }

    public static DriverResponse from(Driver driver, UserLocalEntity user, String phone) {
        return new DriverResponse(
                driver.userId(),
                driver.fleetId(),
                null,
                user != null ? user.getFirstName() : null,
                user != null ? user.getLastName() : null,
                user != null ? user.getEmail() : null,
                phone,
                user != null ? user.getUsername() : null,
                driver.licenceNumber(),
                driver.status(),
                driver.assignedVehicleId(),
                driver.photoUrl()
        );
    }

    public String fullName() {
        if (firstName == null && lastName == null) return licenceNumber;
        return ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
    }
}
