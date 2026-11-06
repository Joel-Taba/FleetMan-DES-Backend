package com.yowyob.fleet.application.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverMessageServiceTest {

    @Test
    void messageIsTrimmed() {
        assertEquals("Bonjour", DriverMessageService.clean("  Bonjour \n"));
    }

    @Test
    void emptyMessageIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> DriverMessageService.clean("   "));
        assertThrows(IllegalArgumentException.class, () -> DriverMessageService.clean(null));
    }

    @Test
    void tooLongMessageIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> DriverMessageService.clean("x".repeat(DriverMessageService.MAX_LENGTH + 1)));
        assertEquals(DriverMessageService.MAX_LENGTH, DriverMessageService.clean("x".repeat(DriverMessageService.MAX_LENGTH)).length());
    }

    @Test
    void notificationPreviewIsShortAndHasNoEmDash() {
        String preview = DriverMessageService.preview("a".repeat(300));
        assertTrue(preview.length() <= 140);
        assertTrue(preview.endsWith("..."));
        assertFalse(preview.contains("—"));
        assertEquals("Court", DriverMessageService.preview("Court"));
    }
}
