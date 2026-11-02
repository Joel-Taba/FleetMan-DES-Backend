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
        String assignedVehicleLabel,
        @Schema(description = "Statut contractuel : ACTIVE ou LEFT (le champ status donne le statut du jour : ACTIVE, ON_LEAVE, SUSPENDED ou LEFT)")
        String contractStatus,
        java.time.LocalDate hireDate,
        java.time.LocalDate leftAt,
        String leftReason,
        @Schema(description = "Absence en cours aujourd'hui")
        com.yowyob.fleet.domain.model.DriverAbsence currentAbsence,
        @Schema(description = "Prochaine absence prévue")
        com.yowyob.fleet.domain.model.DriverAbsence nextAbsence
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
                assignedVehicleLabel,
                driver.status(),
                null,
                null,
                null,
                null,
                null
        );
    }

    /** Ajoute contrat et absences : status devient le statut du jour. */
    public DriverResponse withEmployment(java.time.LocalDate hireDate, java.time.LocalDate leftAt, String leftReason,
                                         java.util.List<com.yowyob.fleet.domain.model.DriverAbsence> absences) {
        java.time.LocalDate today = java.time.LocalDate.now();
        var current = com.yowyob.fleet.domain.model.DriverAbsence.current(absences, today).orElse(null);
        var next = absences.stream()
                .filter(a -> !a.isCancelled() && a.startDate().isAfter(today))
                .min(java.util.Comparator.comparing(com.yowyob.fleet.domain.model.DriverAbsence::startDate))
                .orElse(null);
        String effective = com.yowyob.fleet.domain.model.DriverAbsence.effectiveStatus(contractStatus, absences, today);
        return new DriverResponse(userId, fleetId, managerId, firstName, lastName, email, phone, username,
                licenceNumber, effective, assignedVehicleId, photoUrl, onActiveTrip, isActive, lastLoginAt,
                approvalStatus, fleetName, assignedVehicleLabel, contractStatus, hireDate, leftAt, leftReason,
                current, next);
    }

    public String fullName() {
        if (firstName == null && lastName == null) return licenceNumber;
        return ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
    }
}
