package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
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
        String photoUrl,
        boolean onActiveTrip,
        Boolean isActive,
        Instant lastLoginAt,
        String approvalStatus,
        String fleetName,
        String assignedVehicleLabel
) {
    public static DriverResponse from(Driver driver, UserLocalEntity user) {
        return from(driver, user, null, false, null, null);
    }

    public static DriverResponse from(Driver driver, UserLocalEntity user, String phone) {
        return from(driver, user, phone, false, null, null);
    }

    public static DriverResponse from(Driver driver, UserLocalEntity user, String phone, boolean onActiveTrip) {
        return from(driver, user, phone, onActiveTrip, null, null);
    }

    public static DriverResponse from(
            Driver driver,
            UserLocalEntity user,
            String phone,
            boolean onActiveTrip,
            String fleetName,
            String assignedVehicleLabel
    ) {
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
                driver.photoUrl() != null ? driver.photoUrl() : (user != null ? user.getPhotoUrl() : null),
                onActiveTrip,
                user != null ? user.isActive() : null,
                user != null ? user.getLastLoginAt() : null,
                user != null ? user.getApprovalStatus() : null,
                fleetName,
                assignedVehicleLabel
        );
    }

    public String fullName() {
        if (firstName == null && lastName == null) return licenceNumber;
        return ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
    }
}
