package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.application.service.DocumentService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Synthèse des tableaux de bord : toute l'entreprise pour l'administrateur, ses flottes pour le gestionnaire. */
public final class CompanyOverviewDtos {

    private CompanyOverviewDtos() {
    }

    public record FleetRow(
            UUID id,
            String name,
            String siteName,
            UUID managerId,
            String managerName,
            String managerStatus,
            long vehicles,
            long available,
            long onTrip,
            long inMaintenance,
            long immobilized,
            long drivers,
            long driversAvailable,
            long activeTrips,
            long openIncidents,
            BigDecimal budget,
            BigDecimal consumed,
            long pendingExpenses
    ) {}

    public record Staff(
            long managers,
            long managersActive,
            long managersSuspended,
            long drivers,
            long driversAvailable,
            long driversAbsent,
            long driversSuspended
    ) {}

    public record Operations(
            long plannedToday,
            long activeTrips,
            long overdueTrips,
            long completedThisMonth,
            BigDecimal distanceThisMonth,
            long openIncidents,
            long criticalIncidents,
            long pendingLoadChanges
    ) {}

    public record ExpenseQueue(long count, BigDecimal amount) {}

    public record CostByType(String type, BigDecimal amount) {}

    public record MonthCost(String month, BigDecimal amount) {}

    public record Activity(
            String kind,
            UUID id,
            String subject,
            String status,
            String detail,
            BigDecimal amount,
            LocalDateTime at
    ) {}

    public record Overview(
            List<FleetRow> fleets,
            Staff staff,
            Operations operations,
            ExpenseQueue expensesToApprove,
            List<CostByType> costsThisMonth,
            List<MonthCost> costTrend,
            DocumentService.ComplianceReport documents,
            List<Activity> recent
    ) {}
}
