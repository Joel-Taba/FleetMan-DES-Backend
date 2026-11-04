package com.yowyob.fleet.application.service;

import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.CompanyOverviewDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Synthèse chiffrée des tableaux de bord, calculée sur les flottes de l'entreprise (administrateur)
 * ou sur celles confiées au gestionnaire. Le consommé vient des dépenses approuvées du mois.
 */
@Service
@RequiredArgsConstructor
public class CompanyOverviewService {

    private static final int RECENT_LIMIT = 12;
    private static final String OPEN_INCIDENT = "i.status NOT IN ('RESOLVED', 'CLOSED', 'CANCELLED')";
    private static final String ABSENT_TODAY = """
            EXISTS (SELECT 1 FROM fleet.driver_absences a WHERE a.driver_id = d.user_id AND a.cancelled_at IS NULL
                    AND a.start_date <= CURRENT_DATE AND (a.end_date IS NULL OR a.end_date >= CURRENT_DATE))
            """;

    private final DatabaseClient db;
    private final DocumentService documents;

    public Mono<CompanyOverviewDtos.Overview> overview(UUID actorId, UUID companyId, UUID managerId) {
        return Mono.zip(
                fleets(companyId, managerId),
                staff(companyId, managerId),
                operations(companyId, managerId),
                expenseQueue(companyId, managerId),
                costsThisMonth(companyId, managerId),
                costTrend(companyId, managerId),
                documents.report(actorId),
                recent(companyId, managerId)
        ).map(t -> new CompanyOverviewDtos.Overview(
                t.getT1(), t.getT2(), t.getT3(), t.getT4(), t.getT5(), t.getT6(), t.getT7(), t.getT8()));
    }

    /** Remplace {SCOPE} par le filtre sur les flottes visibles et lie ses paramètres. */
    private DatabaseClient.GenericExecuteSpec scoped(String sql, UUID companyId, UUID managerId) {
        String scope = "f.company_id = :company AND f.deleted_at IS NULL"
                + (managerId != null ? " AND f.manager_id = :manager" : "");
        DatabaseClient.GenericExecuteSpec spec = db.sql(sql.replace("{SCOPE}", scope)).bind("company", companyId);
        return managerId != null ? spec.bind("manager", managerId) : spec;
    }

    private Mono<List<CompanyOverviewDtos.FleetRow>> fleets(UUID companyId, UUID managerId) {
        return scoped("""
                SELECT f.id, f.name, s.name AS site_name, f.manager_id, fm.account_status,
                       NULLIF(TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), '') AS manager_name,
                       (SELECT COUNT(*) FROM fleet.vehicles v WHERE v.fleet_id = f.id AND v.deleted_at IS NULL AND v.status <> 'RETIRED') AS vehicles,
                       (SELECT COUNT(*) FROM fleet.vehicles v WHERE v.fleet_id = f.id AND v.deleted_at IS NULL AND v.status = 'AVAILABLE') AS available,
                       (SELECT COUNT(*) FROM fleet.vehicles v WHERE v.fleet_id = f.id AND v.deleted_at IS NULL AND v.status = 'ON_TRIP') AS on_trip,
                       (SELECT COUNT(*) FROM fleet.vehicles v WHERE v.fleet_id = f.id AND v.deleted_at IS NULL AND v.status = 'MAINTENANCE') AS in_maintenance,
                       (SELECT COUNT(*) FROM fleet.vehicles v WHERE v.fleet_id = f.id AND v.deleted_at IS NULL AND v.status = 'IMMOBILIZED') AS immobilized,
                       (SELECT COUNT(*) FROM fleet.drivers d WHERE d.fleet_id = f.id AND d.deleted_at IS NULL AND d.status <> 'LEFT') AS drivers,
                       (SELECT COUNT(*) FROM fleet.drivers d WHERE d.fleet_id = f.id AND d.deleted_at IS NULL AND d.status = 'ACTIVE' AND NOT %s) AS drivers_available,
                       (SELECT COUNT(*) FROM fleet.trips t WHERE t.fleet_id = f.id AND t.deleted_at IS NULL AND t.status IN ('DEPARTED', 'ARRIVED')) AS active_trips,
                       (SELECT COUNT(*) FROM fleet.incidents i JOIN fleet.vehicles v ON v.id = i.vehicle_id WHERE v.fleet_id = f.id AND %s) AS open_incidents,
                       (SELECT b.amount FROM fleet.budgets b WHERE b.scope = 'FLEET' AND b.entity_id = f.id
                          AND b.budget_month = DATE_TRUNC('month', CURRENT_DATE)::date LIMIT 1) AS budget,
                       (SELECT COALESCE(SUM(e.amount), 0) FROM fleet.expenses e WHERE e.fleet_id = f.id AND e.status = 'APPROVED'
                          AND e.expense_date >= DATE_TRUNC('month', CURRENT_DATE)) AS consumed,
                       (SELECT COUNT(*) FROM fleet.expenses e WHERE e.fleet_id = f.id AND e.status = 'PENDING') AS pending_expenses
                FROM fleet.fleets f
                LEFT JOIN fleet.company_sites s ON s.id = f.site_id
                LEFT JOIN fleet.users u ON u.id = f.manager_id
                LEFT JOIN fleet.fleet_managers fm ON fm.user_id = f.manager_id
                WHERE {SCOPE}
                ORDER BY LOWER(f.name)
                """.formatted(ABSENT_TODAY, OPEN_INCIDENT), companyId, managerId)
                .map((row, meta) -> new CompanyOverviewDtos.FleetRow(
                        row.get("id", UUID.class), row.get("name", String.class), row.get("site_name", String.class),
                        row.get("manager_id", UUID.class), row.get("manager_name", String.class), row.get("account_status", String.class),
                        row.get("vehicles", Long.class), row.get("available", Long.class), row.get("on_trip", Long.class),
                        row.get("in_maintenance", Long.class), row.get("immobilized", Long.class),
                        row.get("drivers", Long.class), row.get("drivers_available", Long.class),
                        row.get("active_trips", Long.class), row.get("open_incidents", Long.class),
                        row.get("budget", BigDecimal.class), row.get("consumed", BigDecimal.class),
                        row.get("pending_expenses", Long.class)))
                .all()
                .collectList();
    }

    private Mono<CompanyOverviewDtos.Staff> staff(UUID companyId, UUID managerId) {
        Mono<long[]> managers = managerId != null
                ? Mono.just(new long[] {0, 0, 0})
                : db.sql("""
                        SELECT COUNT(*) FILTER (WHERE fm.account_status <> 'CLOSED') AS total,
                               COUNT(*) FILTER (WHERE fm.account_status = 'ACTIVE') AS active,
                               COUNT(*) FILTER (WHERE fm.account_status = 'SUSPENDED') AS suspended
                        FROM fleet.fleet_managers fm JOIN fleet.users u ON u.id = fm.user_id AND u.deleted_at IS NULL
                        WHERE fm.created_by_admin_id = :company
                        """)
                .bind("company", companyId)
                .map((row, meta) -> new long[] {row.get("total", Long.class), row.get("active", Long.class), row.get("suspended", Long.class)})
                .one();
        Mono<long[]> drivers = scoped("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE d.status = 'ACTIVE' AND NOT %1$s) AS available,
                       COUNT(*) FILTER (WHERE d.status = 'ON_LEAVE' OR %1$s) AS absent,
                       COUNT(*) FILTER (WHERE d.status = 'SUSPENDED') AS suspended
                FROM fleet.drivers d JOIN fleet.fleets f ON f.id = d.fleet_id
                WHERE d.deleted_at IS NULL AND d.status <> 'LEFT' AND {SCOPE}
                """.formatted(ABSENT_TODAY), companyId, managerId)
                .map((row, meta) -> new long[] {row.get("total", Long.class), row.get("available", Long.class),
                        row.get("absent", Long.class), row.get("suspended", Long.class)})
                .one();
        return Mono.zip(managers, drivers).map(t -> new CompanyOverviewDtos.Staff(
                t.getT1()[0], t.getT1()[1], t.getT1()[2], t.getT2()[0], t.getT2()[1], t.getT2()[2], t.getT2()[3]));
    }

    private Mono<CompanyOverviewDtos.Operations> operations(UUID companyId, UUID managerId) {
        return scoped("""
                SELECT
                  (SELECT COUNT(*) FROM fleet.trips t JOIN fleet.fleets f ON f.id = t.fleet_id
                   WHERE {SCOPE} AND t.deleted_at IS NULL AND t.status = 'SCHEDULED' AND t.start_date = CURRENT_DATE) AS planned_today,
                  (SELECT COUNT(*) FROM fleet.trips t JOIN fleet.fleets f ON f.id = t.fleet_id
                   WHERE {SCOPE} AND t.deleted_at IS NULL AND t.status IN ('DEPARTED', 'ARRIVED')) AS active_trips,
                  (SELECT COUNT(*) FROM fleet.trips t JOIN fleet.fleets f ON f.id = t.fleet_id
                   WHERE {SCOPE} AND t.deleted_at IS NULL AND t.status IN ('DEPARTED', 'ARRIVED')
                     AND t.scheduled_return_datetime < LOCALTIMESTAMP) AS overdue_trips,
                  (SELECT COUNT(*) FROM fleet.trips t JOIN fleet.fleets f ON f.id = t.fleet_id
                   WHERE {SCOPE} AND t.deleted_at IS NULL AND t.status = 'COMPLETED'
                     AND t.return_registered_at >= DATE_TRUNC('month', CURRENT_DATE)) AS completed_month,
                  (SELECT COALESCE(SUM(t.computed_distance_km), 0) FROM fleet.trips t JOIN fleet.fleets f ON f.id = t.fleet_id
                   WHERE {SCOPE} AND t.deleted_at IS NULL AND t.status = 'COMPLETED'
                     AND t.return_registered_at >= DATE_TRUNC('month', CURRENT_DATE)) AS distance_month,
                  (SELECT COUNT(*) FROM fleet.incidents i JOIN fleet.vehicles v ON v.id = i.vehicle_id JOIN fleet.fleets f ON f.id = v.fleet_id
                   WHERE {SCOPE} AND %1$s) AS open_incidents,
                  (SELECT COUNT(*) FROM fleet.incidents i JOIN fleet.vehicles v ON v.id = i.vehicle_id JOIN fleet.fleets f ON f.id = v.fleet_id
                   WHERE {SCOPE} AND %1$s AND i.severity IN ('HIGH', 'CRITICAL')) AS critical_incidents,
                  (SELECT COUNT(*) FROM fleet.trip_mission_submissions m JOIN fleet.trips t ON t.id = m.trip_id JOIN fleet.fleets f ON f.id = t.fleet_id
                   WHERE {SCOPE} AND m.status = 'PENDING') AS pending_load_changes
                """.formatted(OPEN_INCIDENT), companyId, managerId)
                .map((row, meta) -> new CompanyOverviewDtos.Operations(
                        row.get("planned_today", Long.class), row.get("active_trips", Long.class), row.get("overdue_trips", Long.class),
                        row.get("completed_month", Long.class), row.get("distance_month", BigDecimal.class),
                        row.get("open_incidents", Long.class), row.get("critical_incidents", Long.class),
                        row.get("pending_load_changes", Long.class)))
                .one();
    }

    /** Dépenses que l'acteur doit valider : celles de niveau administrateur ou gestionnaire selon son rôle. */
    private Mono<CompanyOverviewDtos.ExpenseQueue> expenseQueue(UUID companyId, UUID managerId) {
        return scoped("""
                SELECT COUNT(*) AS n, COALESCE(SUM(e.amount), 0) AS total
                FROM fleet.expenses e JOIN fleet.fleets f ON f.id = e.fleet_id
                WHERE {SCOPE} AND e.status = 'PENDING' AND e.approval_level = '%s'
                """.formatted(managerId != null ? "MANAGER" : "ADMIN"), companyId, managerId)
                .map((row, meta) -> new CompanyOverviewDtos.ExpenseQueue(row.get("n", Long.class), row.get("total", BigDecimal.class)))
                .one();
    }

    private Mono<List<CompanyOverviewDtos.CostByType>> costsThisMonth(UUID companyId, UUID managerId) {
        return scoped("""
                SELECT e.expense_type, SUM(e.amount) AS total
                FROM fleet.expenses e JOIN fleet.fleets f ON f.id = e.fleet_id
                WHERE {SCOPE} AND e.status = 'APPROVED' AND e.expense_date >= DATE_TRUNC('month', CURRENT_DATE)
                GROUP BY e.expense_type ORDER BY total DESC
                """, companyId, managerId)
                .map((row, meta) -> new CompanyOverviewDtos.CostByType(row.get("expense_type", String.class), row.get("total", BigDecimal.class)))
                .all()
                .collectList();
    }

    private Mono<List<CompanyOverviewDtos.MonthCost>> costTrend(UUID companyId, UUID managerId) {
        return scoped("""
                SELECT TO_CHAR(m, 'YYYY-MM') AS month,
                       (SELECT COALESCE(SUM(e.amount), 0) FROM fleet.expenses e JOIN fleet.fleets f ON f.id = e.fleet_id
                        WHERE {SCOPE} AND e.status = 'APPROVED'
                          AND e.expense_date >= m AND e.expense_date < m + INTERVAL '1 month') AS total
                FROM GENERATE_SERIES(DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '5 months', DATE_TRUNC('month', CURRENT_DATE), INTERVAL '1 month') AS m
                ORDER BY m
                """, companyId, managerId)
                .map((row, meta) -> new CompanyOverviewDtos.MonthCost(row.get("month", String.class), row.get("total", BigDecimal.class)))
                .all()
                .collectList();
    }

    private Mono<List<CompanyOverviewDtos.Activity>> recent(UUID companyId, UUID managerId) {
        return scoped("""
                SELECT * FROM (
                    SELECT 'INCIDENT' AS kind, i.id, i.vehicle_registration AS subject, i.status, i.type AS detail,
                           CAST(NULL AS NUMERIC) AS amount, i.incident_date_time AS at
                    FROM fleet.incidents i JOIN fleet.vehicles v ON v.id = i.vehicle_id JOIN fleet.fleets f ON f.id = v.fleet_id
                    WHERE {SCOPE}
                    UNION ALL
                    SELECT 'TRIP', t.id, t.trip_code, t.status, t.destination_location, NULL,
                           COALESCE(t.return_registered_at, t.arrived_at, t.departure_registered_at)
                    FROM fleet.trips t JOIN fleet.fleets f ON f.id = t.fleet_id
                    WHERE {SCOPE} AND t.deleted_at IS NULL AND t.departure_registered_at IS NOT NULL
                    UNION ALL
                    SELECT 'EXPENSE', e.id, e.vehicle_registration, e.status, e.expense_type, e.amount, e.expense_date
                    FROM fleet.expenses e JOIN fleet.fleets f ON f.id = e.fleet_id
                    WHERE {SCOPE}
                    UNION ALL
                    SELECT 'DOCUMENT', x.id, v.license_plate, x.review_status, x.doc_type, NULL, x.created_at
                    FROM fleet.documents x JOIN fleet.vehicles v ON v.id = x.target_id JOIN fleet.fleets f ON f.id = v.fleet_id
                    WHERE {SCOPE} AND x.target_type = 'VEHICLE' AND x.archived_at IS NULL
                    UNION ALL
                    SELECT 'DOCUMENT', x.id, NULLIF(TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), ''),
                           x.review_status, x.doc_type, NULL, x.created_at
                    FROM fleet.documents x JOIN fleet.drivers d ON d.user_id = x.target_id
                    JOIN fleet.fleets f ON f.id = d.fleet_id JOIN fleet.users u ON u.id = d.user_id
                    WHERE {SCOPE} AND x.target_type = 'DRIVER' AND x.archived_at IS NULL
                ) activity
                WHERE at IS NOT NULL
                ORDER BY at DESC
                LIMIT %d
                """.formatted(RECENT_LIMIT), companyId, managerId)
                .map((row, meta) -> new CompanyOverviewDtos.Activity(
                        row.get("kind", String.class), row.get("id", UUID.class), row.get("subject", String.class),
                        row.get("status", String.class), row.get("detail", String.class),
                        row.get("amount", BigDecimal.class), row.get("at", LocalDateTime.class)))
                .all()
                .collectList();
    }
}
