package com.yowyob.fleet.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cycle de vie véhicule, normalisations et statut du jour d'un chauffeur. */
class VehicleDriverLifecycleTest {

    @Test
    void onlyAvailableOrOnTripVehiclesAreDispatchable() {
        assertTrue(VehicleLifecycle.isDispatchable("AVAILABLE"));
        assertTrue(VehicleLifecycle.isDispatchable("ON_TRIP"));
        assertFalse(VehicleLifecycle.isDispatchable("MAINTENANCE"));
        assertFalse(VehicleLifecycle.isDispatchable("IMMOBILIZED"));
        assertFalse(VehicleLifecycle.isDispatchable("RETIRED"));
        assertFalse(VehicleLifecycle.isInService("RETIRED"));
    }

    @Test
    void legacyStatusesAreMappedAndUnknownRejected() {
        assertEquals("IMMOBILIZED", VehicleLifecycle.normalizeStatus("out_of_service"));
        assertEquals("AVAILABLE", VehicleLifecycle.normalizeStatus("PARKED"));
        assertEquals("MAINTENANCE", VehicleLifecycle.normalizeStatus("IN_MAINTENANCE"));
        assertNull(VehicleLifecycle.normalizeStatus("SOLD"));
    }

    @Test
    void platesAreComparedWithoutSeparators() {
        assertEquals("LT 892 CE", VehicleLifecycle.normalizePlate("  lt   892 ce "));
        assertEquals(VehicleLifecycle.plateKey("LT-892-CE"), VehicleLifecycle.plateKey("lt 892 ce"));
    }

    @Test
    void fuelAndTransmissionLabelsAreNormalized() {
        assertEquals("DIESEL", VehicleLifecycle.normalizeFuelType("Gasoil"));
        assertEquals("GASOLINE", VehicleLifecycle.normalizeFuelType("Essence"));
        assertEquals("ELECTRIC", VehicleLifecycle.normalizeFuelType("électrique"));
        assertNull(VehicleLifecycle.normalizeFuelType("charbon"));
        assertEquals("AUTOMATIC", VehicleLifecycle.normalizeTransmission("Automatique"));
        assertEquals("MANUAL", VehicleLifecycle.normalizeTransmission("manuelle"));
    }

    @Test
    void manufacturingYearMustBePlausible() {
        assertTrue(VehicleLifecycle.isPlausibleYear(2015));
        assertFalse(VehicleLifecycle.isPlausibleYear(1900));
        assertFalse(VehicleLifecycle.isPlausibleYear(LocalDate.now().getYear() + 3));
    }

    private DriverAbsence absence(String type, String from, String to) {
        return new DriverAbsence(UUID.randomUUID(), UUID.randomUUID(), type, LocalDate.parse(from),
                to == null ? null : LocalDate.parse(to), null, null, null, null);
    }

    @Test
    void driverStatusFollowsAbsencesAndReturnsAutomatically() {
        var leave = absence("LEAVE", "2026-11-02", "2026-11-06");
        assertEquals("ON_LEAVE", DriverAbsence.effectiveStatus("ACTIVE", List.of(leave), LocalDate.parse("2026-11-06")));
        assertEquals("ACTIVE", DriverAbsence.effectiveStatus("ACTIVE", List.of(leave), LocalDate.parse("2026-11-07")));
        assertEquals("LEFT", DriverAbsence.effectiveStatus("LEFT", List.of(leave), LocalDate.parse("2026-11-03")));
    }

    @Test
    void openSuspensionWinsOverLeave() {
        var leave = absence("LEAVE", "2026-11-01", "2026-11-30");
        var suspension = absence("SUSPENSION", "2026-11-10", null);
        assertEquals("SUSPENDED", DriverAbsence.effectiveStatus("ACTIVE", List.of(leave, suspension), LocalDate.parse("2026-11-12")));
        assertTrue(suspension.overlaps(LocalDate.parse("2030-01-01"), LocalDate.parse("2030-01-02")));
    }

    @Test
    void cancelledAbsenceIsIgnored() {
        var cancelled = new DriverAbsence(UUID.randomUUID(), UUID.randomUUID(), "LEAVE", LocalDate.parse("2026-11-02"),
                LocalDate.parse("2026-11-06"), null, null, null, java.time.Instant.now());
        assertFalse(cancelled.coversDay(LocalDate.parse("2026-11-03")));
    }
}
