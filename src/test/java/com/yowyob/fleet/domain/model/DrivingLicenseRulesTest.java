package com.yowyob.fleet.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrivingLicenseRulesTest {

    @Test
    void defaultCategory_followsVehicleType() {
        assertEquals("B", DrivingLicenseRules.defaultCategoryFor("car"));
        assertEquals("C", DrivingLicenseRules.defaultCategoryFor("TRUCK"));
        assertEquals("D", DrivingLicenseRules.defaultCategoryFor("BUS"));
        assertEquals("A", DrivingLicenseRules.defaultCategoryFor("BIKE"));
        assertNull(DrivingLicenseRules.defaultCategoryFor("SPACESHIP"));
    }

    @Test
    void parse_acceptsFreeFormSeparators() {
        assertEquals(Set.of("B", "C", "D"), DrivingLicenseRules.parse(" b, c;D "));
        assertEquals(Set.of(), DrivingLicenseRules.parse(null));
    }

    @Test
    void covers_requiresTheExactCategory() {
        assertTrue(DrivingLicenseRules.covers("B,C", "c"));
        assertFalse(DrivingLicenseRules.covers("B", "C"));
        assertTrue(DrivingLicenseRules.covers(null, null));
        assertFalse(DrivingLicenseRules.covers(null, "B"));
    }
}
