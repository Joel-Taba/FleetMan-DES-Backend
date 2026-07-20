package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.ports.out.StatisticsPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SuperAdminDashboardStatsResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SuperAdminDashboardStatsResponse.SignupTrendPoint;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SuperAdminDashboardStatsResponse.UserTypeSlice;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SuperAdminDashboardService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Douala");
    private static final DateTimeFormatter DAY_LABEL =
            DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH);

    private final StatisticsPort statisticsPort;

    public Mono<SuperAdminDashboardStatsResponse> getDashboardStats(String period) {
        Instant since = resolvePeriodStart(period);
        String normalized = normalizePeriod(period);

        return Mono.zip(
                        statisticsPort.countActiveAdminsSince(since),
                        statisticsPort.countFleetManagersSince(since),
                        statisticsPort.countFleetsSince(since),
                        statisticsPort.countVehiclesSince(since),
                        statisticsPort.countDriversSince(since),
                        statisticsPort.signupTrendSince(since),
                        statisticsPort.userTypeDistributionSince(since)
                )
                .map(t -> new SuperAdminDashboardStatsResponse(
                        t.getT1(),
                        t.getT2(),
                        t.getT3(),
                        t.getT4(),
                        t.getT5(),
                        normalized,
                        t.getT6(),
                        t.getT7()
                ));
    }

    static Instant resolvePeriodStart(String period) {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        return switch (normalizePeriod(period)) {
            case "today" -> now.toLocalDate().atStartOfDay(ZONE).toInstant();
            case "month" -> now.withDayOfMonth(1).toLocalDate().atStartOfDay(ZONE).toInstant();
            default -> now.minusDays(7).toLocalDate().atStartOfDay(ZONE).toInstant();
        };
    }

    static String normalizePeriod(String period) {
        if (period == null || period.isBlank()) {
            return "7d";
        }
        String p = period.trim().toLowerCase(Locale.ROOT);
        return switch (p) {
            case "today", "aujourd'hui", "aujourdhui" -> "today";
            case "month", "ce mois", "ce-mois" -> "month";
            default -> "7d";
        };
    }
}
