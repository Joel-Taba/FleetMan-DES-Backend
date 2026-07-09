package com.yowyob.fleet.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AppSettingsService {

    private static final String GRACE_KEY = "subscription_grace_days";

    private final DatabaseClient db;

    public Mono<Integer> getSubscriptionGraceDays() {
        return db.sql("SELECT setting_value FROM fleet.app_settings WHERE setting_key = :key")
                .bind("key", GRACE_KEY)
                .fetch()
                .one()
                .map(row -> Integer.parseInt(row.get("setting_value").toString()))
                .defaultIfEmpty(7);
    }

    public Mono<Integer> updateSubscriptionGraceDays(int days) {
        int safe = Math.max(0, Math.min(365, days));
        return db.sql("""
                INSERT INTO fleet.app_settings (setting_key, setting_value, updated_at)
                VALUES (:key, :value, NOW())
                ON CONFLICT (setting_key)
                DO UPDATE SET setting_value = EXCLUDED.setting_value, updated_at = NOW()
                """)
                .bind("key", GRACE_KEY)
                .bind("value", String.valueOf(safe))
                .fetch()
                .rowsUpdated()
                .thenReturn(safe);
    }
}
