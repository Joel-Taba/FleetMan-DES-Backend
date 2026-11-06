package com.yowyob.fleet.domain.model;

import com.yowyob.fleet.domain.exception.MaintenanceWorkflowException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Cycle de vie d'une maintenance : demande du chauffeur → planification → atelier → clôture / annulation. */
class MaintenanceWorkflowTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 10, 0);

    private Maintenance request() {
        Maintenance m = new Maintenance(null, "Bruit de freins", null, NOW, "Grincement à l'avant", null, null,
                UUID.randomUUID(), "DL-203-FF", UUID.randomUUID(), "Bella");
        m.initWorkflow(Maintenance.Status.REQUESTED, "CORRECTIVE", false, null, m.getDriverId(), NOW);
        return m;
    }

    @Test
    void driverRequestStartsRequestedAndOpen() {
        Maintenance m = request();
        assertEquals(Maintenance.Status.REQUESTED, m.getStatus());
        assertTrue(m.getStatus().isOpen());
        assertEquals("CORRECTIVE", m.getMaintenanceType());
        assertNotNull(m.getRequestedBy());
    }

    @Test
    void emergencyTypeIsAlwaysUrgent() {
        Maintenance m = new Maintenance(null, "Panne", null, NOW, null, null, null, UUID.randomUUID(), "X", null, null);
        m.initWorkflow(Maintenance.Status.REQUESTED, "EMERGENCY", false, null, null, NOW);
        assertTrue(m.isUrgent());
    }

    @Test
    void legacyEntryWithoutStatusIsCompleted() {
        Maintenance m = new Maintenance(null, "Vidange", BigDecimal.TEN, NOW, null, null, null, UUID.randomUUID(), "X", null, null);
        m.initWorkflow(null, null, false, null, null, NOW);
        assertEquals(Maintenance.Status.COMPLETED, m.getStatus());
        assertEquals(NOW, m.getCompletedAt());
    }

    @Test
    void planRequiresDateAndKeepsGarage() {
        Maintenance m = request();
        assertThrows(MaintenanceWorkflowException.class, () -> m.plan(null, "Garage"));
        m.plan(NOW.plusDays(2), "Garage Central");
        assertEquals(Maintenance.Status.PLANNED, m.getStatus());
        assertEquals("Garage Central", m.getLocationName());
        // Replanification autorisée tant que l'intervention n'a pas commencé.
        m.plan(NOW.plusDays(3), null);
        assertEquals(NOW.plusDays(3), m.getPlannedAt());
        assertEquals("Garage Central", m.getLocationName());
    }

    @Test
    void startThenCompleteSetsTimestampsCostAndReport() {
        Maintenance m = request();
        m.plan(NOW.plusDays(1), null);
        m.start(NOW.plusDays(1));
        assertEquals(Maintenance.Status.IN_PROGRESS, m.getStatus());
        assertThrows(MaintenanceWorkflowException.class, () -> m.plan(NOW.plusDays(4), null));
        m.complete(NOW.plusDays(2), new BigDecimal("45000"), "Plaquettes changées");
        assertEquals(Maintenance.Status.COMPLETED, m.getStatus());
        assertEquals(new BigDecimal("45000"), m.getCost());
        assertEquals("Plaquettes changées", m.getReport());
        assertEquals(NOW.plusDays(1), m.getStartedAt());
        assertFalse(m.getStatus().isOpen());
    }

    @Test
    void cancelNeedsReasonAndClosedMaintenanceIsFrozen() {
        Maintenance m = request();
        assertThrows(MaintenanceWorkflowException.class, () -> m.cancel(NOW, " "));
        m.cancel(NOW, "Doublon");
        assertEquals(Maintenance.Status.CANCELLED, m.getStatus());
        assertEquals("Doublon", m.getCancelReason());
        assertThrows(MaintenanceWorkflowException.class, () -> m.start(NOW));
        assertThrows(MaintenanceWorkflowException.class, () -> m.complete(NOW, null, null));
        assertThrows(MaintenanceWorkflowException.class, () -> m.cancel(NOW, "encore"));
    }

    @Test
    void cannotBeCreatedCancelledNorPlannedWithoutDate() {
        Maintenance m = new Maintenance(null, "X", null, NOW, null, null, null, UUID.randomUUID(), "X", null, null);
        assertThrows(MaintenanceWorkflowException.class,
                () -> m.initWorkflow(Maintenance.Status.CANCELLED, null, false, null, null, NOW));
        assertThrows(MaintenanceWorkflowException.class,
                () -> m.initWorkflow(Maintenance.Status.PLANNED, null, false, null, null, NOW));
    }
}
