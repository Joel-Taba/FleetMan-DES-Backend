package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.ports.out.ResourceLockPort;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Verrous consultatifs PostgreSQL de niveau transaction ({@code pg_advisory_xact_lock}) :
 * libérés automatiquement au commit ou au rollback, ils n'exigent aucune table dédiée.
 * Les clés sont triées pour que deux transactions verrouillant les mêmes ressources le
 * fassent toujours dans le même ordre (pas d'interblocage).
 */
@Component
@RequiredArgsConstructor
public class PostgresResourceLockAdapter implements ResourceLockPort {

    private static final String KEY_PREFIX = "fleet-resource:";

    private final DatabaseClient db;

    @Override
    public Mono<Void> lock(UUID... resourceIds) {
        var keys = Arrays.stream(resourceIds)
                .filter(Objects::nonNull)
                .map(id -> KEY_PREFIX + id)
                .distinct()
                .sorted()
                .toList();
        return Flux.fromIterable(keys)
                .concatMap(key -> db.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                        .bind("key", key)
                        .map((row, meta) -> 1)
                        .first())
                .then();
    }
}
