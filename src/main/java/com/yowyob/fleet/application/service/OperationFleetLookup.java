package com.yowyob.fleet.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Flotte figée des opérations (incidents, entretiens, pleins), lue en une requête par liste.
 * Elle reste celle de la création même si le véhicule a changé de flotte depuis.
 */
@Service
@RequiredArgsConstructor
public class OperationFleetLookup {

    private static final Set<String> TABLES = Set.of("incidents", "maintenances", "fuel_recharges");

    private final DatabaseClient db;

    public Mono<Map<UUID, UUID>> fleets(String table, Collection<UUID> ids) {
        if (!TABLES.contains(table)) return Mono.error(new IllegalArgumentException("table"));
        List<UUID> list = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (list.isEmpty()) return Mono.just(Map.of());
        return db.sql("SELECT id, fleet_id FROM fleet." + table + " WHERE id IN (:ids)")
                .bind("ids", list)
                .map((row, meta) -> Map.entry(row.get("id", UUID.class), java.util.Optional.ofNullable(row.get("fleet_id", UUID.class))))
                .all()
                .collect(HashMap<UUID, UUID>::new, (m, e) -> e.getValue().ifPresent(f -> m.put(e.getKey(), f)))
                .map(m -> (Map<UUID, UUID>) m);
    }

    public <T> Flux<T> attach(Flux<T> items, String table, Function<T, UUID> id, BiFunction<T, UUID, T> with) {
        return items.collectList().flatMapMany(list -> fleets(table, list.stream().map(id).toList())
                .flatMapMany(map -> Flux.fromIterable(list).map(item -> with.apply(item, map.get(id.apply(item))))));
    }
}
