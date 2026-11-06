package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.DriverException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DriverRegistrationRulesTest {

    @Test
    void codesAvoidAmbiguousCharactersAndAreNormalizedOnInput() {
        for (int i = 0; i < 200; i++) {
            String code = DriverRegistrationService.newCode(8);
            assertEquals(8, code.length());
            assertFalse(code.matches(".*[01OIL].*"), code);
        }
        assertEquals("ABCD2345", DriverRegistrationService.normalizeCode(" abcd-2345 "));
        assertEquals("ABCD-2345", DriverRegistrationService.formatCode("ABCD2345"));
    }

    @Test
    void passwordFollowsKernelPolicy() {
        assertThrows(DriverException.class, () -> DriverRegistrationService.checkPassword("Ab1!"));
        assertThrows(DriverException.class, () -> DriverRegistrationService.checkPassword("chauffeur2026!"));
        assertThrows(DriverException.class, () -> DriverRegistrationService.checkPassword("Chauffeur2026"));
        assertDoesNotThrow(() -> DriverRegistrationService.checkPassword("Chauffeur2026!"));
    }

    @Test
    void contactsAreNormalizedAndMasked() {
        assertEquals("+237677000204", DriverRegistrationService.normalizePhone("+237 677 00 02 04"));
        assertEquals("jean@mail.cm", DriverRegistrationService.normalizeEmail(" Jean@Mail.CM "));
        assertNull(DriverRegistrationService.normalizeEmail("  "));
        assertEquals("j***@mail.cm", DriverRegistrationService.maskEmail("jean@mail.cm"));
        assertEquals("***204", DriverRegistrationService.maskPhone("+237677000204"));
        assertEquals("helene.ngo.biyong", DriverRegistrationService.usernameBase("Hélène", "Ngo Biyong"));
    }

    @Test
    void missingProfileListsEveryEmptyField() {
        var empty = new DriverOnboardingService.Profile("A", "B", null, null, "L", null, null, null, null, null, "F");
        assertEquals(List.of("PHOTO", "PHONE", "BIRTH_DATE", "ADDRESS", "EMERGENCY_CONTACT_NAME", "EMERGENCY_CONTACT_PHONE"),
                DriverOnboardingService.missingProfile(empty));
        var full = new DriverOnboardingService.Profile("A", "B", "+237600000001", null, "L", "/p.jpg",
                LocalDate.of(1990, 1, 1), "Douala", "C", "+237600000002", "F");
        assertTrue(DriverOnboardingService.missingProfile(full).isEmpty());
    }
}
