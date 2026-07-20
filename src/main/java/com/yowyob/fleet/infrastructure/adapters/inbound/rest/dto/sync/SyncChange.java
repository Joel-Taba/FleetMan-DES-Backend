package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SyncChange(
        String entityType,
        UUID entityId,
        Instant updatedAt,
        Map<String, Object> payload
) {}
