package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Vues du super-administrateur sur les abonnements des entreprises clientes. */
public final class SubscriptionAdminDtos {

    private SubscriptionAdminDtos() {
    }

    public record CompanyRow(
            UUID id,
            String name,
            String adminName,
            String username,
            String email,
            String phone,
            String city,
            UUID planId,
            String planName,
            BigDecimal monthlyPrice,
            BigDecimal annualPrice,
            String billingCycle,
            String subscriptionStatus,
            String state,
            LocalDate subscriptionStart,
            LocalDate subscriptionEnd,
            Long daysLeft,
            int graceDays,
            BigDecimal monthlyRevenue,
            int maxFleets,
            int maxVehicles,
            int maxDrivers,
            long fleets,
            long vehicles,
            long drivers,
            long managers,
            LocalDateTime lastPaymentAt,
            long pendingOrders,
            String suspensionReason,
            LocalDateTime suspendedAt,
            LocalDateTime createdAt
    ) {}

    public record OrderRow(
            UUID id,
            UUID companyId,
            String companyName,
            String planName,
            String billingCycle,
            BigDecimal amount,
            String currency,
            String status,
            String paymentProvider,
            String paymentMethod,
            String paymentReference,
            String paymentNote,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDateTime createdAt,
            LocalDateTime paidAt
    ) {}

    public record EventRow(
            UUID id,
            String type,
            String actorName,
            String planName,
            LocalDate periodEnd,
            BigDecimal amount,
            String reference,
            String reason,
            LocalDateTime at
    ) {}

    public record CompanyDetail(
            CompanyRow company,
            String legalName,
            String legalForm,
            String registrationNumber,
            String taxId,
            String sector,
            String address,
            String companyPhone,
            String companyEmail,
            List<OrderRow> orders,
            List<EventRow> events
    ) {}

    public record StateCount(String state, long count) {}

    public record PlanShare(String planName, long companies, BigDecimal monthlyRevenue) {}

    public record MonthRevenue(String month, BigDecimal amount) {}

    public record Overview(
            long companies,
            long payingCompanies,
            long freeCompanies,
            long pendingRequests,
            BigDecimal monthlyRecurringRevenue,
            BigDecimal annualRecurringRevenue,
            BigDecimal collectedThisMonth,
            BigDecimal collectedTotal,
            long pendingOrders,
            BigDecimal pendingOrdersAmount,
            List<StateCount> states,
            List<PlanShare> plans,
            List<MonthRevenue> revenue,
            List<CompanyRow> expiringSoon,
            boolean paymentSimulation
    ) {}

    public record ChangePlanRequest(UUID planId, String billingCycle, String reason) {}

    public record ExtendRequest(int days, String reason) {}

    public record ReasonRequest(String reason) {}

    public record RecordPaymentRequest(String method, String reference, String note) {}
}
