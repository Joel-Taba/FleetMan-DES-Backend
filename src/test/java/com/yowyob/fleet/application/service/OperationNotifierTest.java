package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.Expense;
import com.yowyob.fleet.domain.model.FuelRecharge;
import com.yowyob.fleet.domain.model.Maintenance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Notifications entre rôles des opérations terrain : bon destinataire, bon code, bons identifiants. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OperationNotifierTest {

    @Mock AppNotifier notifier;
    @Mock OperationLookup lookup;

    private OperationNotifier service;
    private final UUID fleetId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID vehicleId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID otherDriver = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OperationNotifier(notifier, lookup);
        when(lookup.localUser(any())).thenAnswer(inv -> Mono.justOrEmpty((UUID) inv.getArgument(0)));
        when(lookup.vehicle(vehicleId)).thenReturn(Mono.just(
                new OperationLookup.VehicleRef(vehicleId, fleetId, "DL-203-FF", companyId, "AVAILABLE")));
        when(lookup.fullName(driverId)).thenReturn(Mono.just("Bella Ndzi"));
        when(lookup.vehicleDrivers(vehicleId)).thenReturn(Flux.just(otherDriver));
        when(lookup.autoExpense(any())).thenReturn(Mono.empty());
        when(notifier.toUser(any(), any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        when(notifier.toUsers(any(), any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        when(notifier.toFleetManager(any(), any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    private FuelRecharge recharge(UUID createdBy) {
        FuelRecharge r = new FuelRecharge(UUID.randomUUID(), new BigDecimal("40"), new BigDecimal("30000"), LocalDateTime.now(),
                null, FuelRecharge.StationName.TOTAL, vehicleId, "DL-203-FF", driverId, "Bella Ndzi");
        r.setStationLabel("Total Bastos");
        r.setOdometerKm(new BigDecimal("84250"));
        r.setCreatedBy(createdBy);
        return r;
    }

    @SuppressWarnings("unchecked")
    @Test
    void fuelDeclaredByDriverNotifiesFleetManagerWithIds() {
        FuelRecharge r = recharge(driverId);
        UUID expenseId = UUID.randomUUID();
        when(lookup.autoExpense(r.getId())).thenReturn(Mono.just(
                new OperationLookup.ExpenseBrief(expenseId, "PENDING", null, "MANAGER", null, new BigDecimal("30000"))));

        StepVerifier.create(service.fuelCreated(r, driverId)).verifyComplete();

        ArgumentCaptor<Map<String, Object>> refs = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notifier).toFleetManager(eq(fleetId), eq(driverId), eq("Plein déclaré"), message.capture(),
                eq(AppNotifier.Level.INFO), eq("FUEL_DECLARED"), refs.capture());
        assertEquals(r.getId().toString(), refs.getValue().get("fuelRechargeId"));
        assertEquals(expenseId.toString(), refs.getValue().get("expenseId"));
        assertTrue(message.getValue().contains("Bella Ndzi"));
        assertTrue(message.getValue().contains("Total Bastos"));
        assertTrue(message.getValue().contains("84250 km"));
        assertTrue(message.getValue().contains("Dépense en attente de validation"));
        assertFalse(message.getValue().contains("—"));
        verify(notifier, never()).toUsers(any(), any(), any(), any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void fuelRecordedByManagerNotifiesTheDriver() {
        FuelRecharge r = recharge(managerId);
        StepVerifier.create(service.fuelCreated(r, managerId)).verifyComplete();

        ArgumentCaptor<Collection<UUID>> to = ArgumentCaptor.forClass(Collection.class);
        verify(notifier).toUsers(to.capture(), eq(managerId), eq("Plein enregistré"), anyString(),
                any(), eq("FUEL_RECORDED"), anyMap());
        assertTrue(to.getValue().contains(driverId));
        verify(notifier, never()).toFleetManager(any(), any(), any(), any(), any(), any(), any());
    }

    private Maintenance maintenance(Maintenance.Status status, UUID requestedBy) {
        Maintenance m = new Maintenance(UUID.randomUUID(), "Bruit de freins", null, LocalDateTime.now(), "Grincement",
                null, "Garage Central", vehicleId, "DL-203-FF", driverId, "Bella Ndzi");
        m.initWorkflow(status, "CORRECTIVE", true, status == Maintenance.Status.PLANNED ? LocalDateTime.now().plusDays(1) : null,
                requestedBy, LocalDateTime.now());
        return m;
    }

    @SuppressWarnings("unchecked")
    @Test
    void maintenanceRequestNotifiesManagerAsWarningWhenUrgent() {
        Maintenance m = maintenance(Maintenance.Status.REQUESTED, driverId);
        StepVerifier.create(service.maintenanceCreated(m, driverId)).verifyComplete();

        ArgumentCaptor<Map<String, Object>> refs = ArgumentCaptor.forClass(Map.class);
        verify(notifier).toFleetManager(eq(fleetId), eq(driverId), eq("Maintenance urgente demandée"), anyString(),
                eq(AppNotifier.Level.WARNING), eq("MAINTENANCE_REQUESTED"), refs.capture());
        assertEquals(m.getId().toString(), refs.getValue().get("maintenanceId"));
    }

    @Test
    void repairOrderWithoutDriverRequesterNotifiesNobody() {
        Maintenance m = maintenance(Maintenance.Status.REQUESTED, null);
        StepVerifier.create(service.maintenanceCreated(m, null)).verifyComplete();
        verify(notifier, never()).toFleetManager(any(), any(), any(), any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void plannedMaintenanceNotifiesRequesterAndVehicleDrivers() {
        Maintenance m = maintenance(Maintenance.Status.REQUESTED, driverId);
        m.plan(LocalDateTime.of(2026, 11, 10, 9, 0), "Garage Central");

        StepVerifier.create(service.maintenanceTransition(m, managerId)).verifyComplete();

        ArgumentCaptor<Collection<UUID>> to = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notifier).toUsers(to.capture(), eq(managerId), eq("Maintenance planifiée"), message.capture(),
                eq(AppNotifier.Level.INFO), eq("MAINTENANCE_PLANNED"), anyMap());
        assertTrue(to.getValue().contains(driverId));
        assertTrue(to.getValue().contains(otherDriver));
        assertTrue(message.getValue().contains("10/11/2026 à 09:00"));
        assertTrue(message.getValue().contains("Garage Central"));
    }

    @Test
    void cancelledMaintenanceCarriesTheReason() {
        Maintenance m = maintenance(Maintenance.Status.REQUESTED, driverId);
        m.cancel(LocalDateTime.now(), "Doublon");
        StepVerifier.create(service.maintenanceTransition(m, managerId)).verifyComplete();
        verify(notifier).toUsers(any(), eq(managerId), eq("Maintenance annulée"), contains("Doublon"),
                eq(AppNotifier.Level.WARNING), eq("MAINTENANCE_CANCELLED"), anyMap());
    }

    private Expense fuelExpense(UUID sourceId) {
        Expense e = new Expense(UUID.randomUUID(), Expense.ExpenseType.FUEL, new BigDecimal("30000"), "Carburant : 40 L",
                LocalDateTime.now(), Expense.SourceType.AUTO, sourceId, vehicleId, "DL-203-FF", fleetId, managerId,
                driverId, "Bella Ndzi", null);
        e.requireValidation();
        e.setCreatedBy(driverId);
        return e;
    }

    @SuppressWarnings("unchecked")
    @Test
    void rejectedFuelExpenseNotifiesDriverWithReasonAndRechargeId() {
        UUID rechargeId = UUID.randomUUID();
        Expense e = fuelExpense(rechargeId);
        e.reject(managerId, "Ticket illisible");

        StepVerifier.create(service.expenseDecided(e, managerId)).verifyComplete();

        ArgumentCaptor<Collection<UUID>> to = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Map<String, Object>> refs = ArgumentCaptor.forClass(Map.class);
        verify(notifier).toUsers(to.capture(), eq(managerId), eq("Dépense rejetée"), contains("Ticket illisible"),
                eq(AppNotifier.Level.WARNING), eq("EXPENSE_REJECTED"), refs.capture());
        assertTrue(to.getValue().contains(driverId));
        assertEquals(rechargeId.toString(), refs.getValue().get("fuelRechargeId"));
        assertEquals(e.getId().toString(), refs.getValue().get("expenseId"));
    }

    @Test
    void approvedExpenseUsesApprovedEvent() {
        Expense e = fuelExpense(UUID.randomUUID());
        e.approve(managerId);
        StepVerifier.create(service.expenseDecided(e, managerId)).verifyComplete();
        verify(notifier).toUsers(any(), eq(managerId), eq("Dépense approuvée"), contains("approuvé"),
                eq(AppNotifier.Level.SUCCESS), eq("EXPENSE_APPROVED"), anyMap());
    }

    @Test
    void adminLevelExpenseNotifiesCompanyAdmin() {
        Expense e = fuelExpense(UUID.randomUUID());
        e.setApprovalLevel(Expense.ApprovalLevel.ADMIN);
        StepVerifier.create(service.expenseCreated(e, null)).verifyComplete();
        verify(notifier).toUser(eq(companyId), isNull(), anyString(), anyString(),
                eq(AppNotifier.Level.WARNING), eq("EXPENSE_ADMIN_APPROVAL"), anyMap());
    }

    @Test
    void manualExpenseByDriverNotifiesManager() {
        Expense e = new Expense(UUID.randomUUID(), Expense.ExpenseType.TOLL, new BigDecimal("2000"), "Péage",
                LocalDateTime.now(), Expense.SourceType.MANUAL, null, vehicleId, "DL-203-FF", fleetId, managerId,
                driverId, "Bella Ndzi", null);
        StepVerifier.create(service.expenseCreated(e, driverId)).verifyComplete();
        verify(notifier).toUser(eq(managerId), eq(driverId), eq("Dépense à valider"), anyString(),
                any(), eq("EXPENSE_SUBMITTED"), anyMap());
    }

    @Test
    void notificationFailureNeverBreaksTheAction() {
        when(lookup.vehicle(vehicleId)).thenReturn(Mono.error(new IllegalStateException("db down")));
        StepVerifier.create(service.fuelCreated(recharge(driverId), driverId)).verifyComplete();
    }

    @Test
    void amountsAreFormattedWithoutEmDash() {
        assertEquals("30 000 XAF", OperationNotifier.amount(new BigDecimal("30000")));
        assertEquals("Maintenance corrective", OperationNotifier.subjectLabel(new Maintenance(null, "CORRECTIVE", null,
                null, null, null, null, vehicleId, null, null, null)));
    }
}
