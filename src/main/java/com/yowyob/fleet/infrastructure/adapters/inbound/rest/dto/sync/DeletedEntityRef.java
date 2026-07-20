package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync;

import java.time.Instant;
import java.util.UUID;

public record DeletedEntityRef(
        String entityType,
        UUID entityId,
        Instant deletedAt
) {}
